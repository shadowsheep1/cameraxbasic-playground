package com.android.example.cameraxbasic.analyzer

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer


class ZxingQrCodeAnalyzer(
        private val onQrCodesDetected: (qrCode: Result) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        val reader = MultiFormatReader()
    }

    /*
        https://developer.android.com/training/camerax/configuration

        Default resolution: The default target resolution setting is 640x480.

        Adjusting both target resolution and corresponding aspect ratio will result
        in a best-supported resolution under 1080p (max analysis resolution).
    */
    override fun analyze(imageProxy: ImageProxy, rotationDegrees: Int) {
        // okay - manage rotation, not needed for QRCode decoding [-;
        // okay - manage it for barcode scanning instead!!!
        try {
            imageProxy.image?.let {
                // ImageProxy uses an ImageReader under the hood:
                // https://developer.android.com/reference/androidx/camera/core/ImageProxy.html
                // That has a default format of YUV_420_888 if not changed.
                // https://developer.android.com/reference/android/graphics/ImageFormat.html#YUV_420_888
                // https://developer.android.com/reference/android/media/ImageReader.html
                if ((it.format == ImageFormat.YUV_420_888
                                || it.format == ImageFormat.YUV_422_888
                                || it.format == ImageFormat.YUV_444_888)
                        && it.planes.size == 3) {
                    val buffer = it.planes[0].buffer // We get the luminance plane only, since we
                    // want to binarize it and we don't wanna take color into consideration.
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    // Create a LuminanceSource.
                    val rotatedImage = RotatedImage(bytes, imageProxy.width, imageProxy.height)

                    rotateImageArray(rotatedImage, rotationDegrees)

                    val source = PlanarYUVLuminanceSource(rotatedImage.byteArray,
                            rotatedImage.width,
                            rotatedImage.height,
                            0,
                            0,
                            rotatedImage.width,
                            rotatedImage.height
                            ,
                            false)

                    // Create a Binarizer
                    val binarizer = HybridBinarizer(source)
                    // Create a BinaryBitmap.
                    val binaryBitmap = BinaryBitmap(binarizer)
                    // Try decoding...
                    val result: Result
                    try {
                        result = reader.decode(binaryBitmap)
                        onQrCodesDetected(result)
                    } catch (e: NotFoundException) {
                        e.printStackTrace()
                    }
                } else {
                    // Manage other image formats
                    // TODO - https://developer.android.com/reference/android/media/Image.html
                }
            }
        } catch (ise: IllegalStateException) {
            ise.printStackTrace()
        }
    }

    // 90, 180. 270 rotation
    private fun rotateImageArray(imageToRotate: RotatedImage, rotationDegrees: Int) {
        if (rotationDegrees == 0) return // no rotation
        if (rotationDegrees % 90 != 0) return // only 90 degree times rotations

        val width = imageToRotate.width
        val height = imageToRotate.height

        val rotatedData = ByteArray(imageToRotate.byteArray.size)
        for (y in 0 until height) { // we scan the array by rows
            for (x in 0 until width) {
                when (rotationDegrees) {
                    90 -> rotatedData[x * height + height - y - 1] =
                            imageToRotate.byteArray[x + y * width] // Fill from top-right toward left (CW)
                    180 -> rotatedData[width * (height - y - 1) + width - x - 1] =
                            imageToRotate.byteArray[x + y * width] // Fill from bottom-right toward up (CW)
                    270 -> rotatedData[y + x * height] =
                            imageToRotate.byteArray[y * width + width - x - 1] // The opposite (CCW) of 90 degrees
                }
            }
        }

        imageToRotate.byteArray = rotatedData

        if (rotationDegrees != 180) {
            imageToRotate.height = width
            imageToRotate.width = height
        }
    }
}

private data class RotatedImage(var byteArray: ByteArray, var width: Int, var height: Int)