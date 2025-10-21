// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.utils

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ScientificUtils {
  @JvmField
  val SCIENTIFIC_MODE_KEY: Key<Unit> = Key("SCIENTIFIC_MODE")
  val DATA_TYPE_KEY: Key<String> = Key("DATA_TYPE")
  const val DEFAULT_IMAGE_FORMAT: String = "png"

  internal suspend fun saveImageToFile(imageFile: VirtualFile, image: BufferedImage) = withContext(Dispatchers.IO) {
    ByteArrayOutputStream().use { outputStream ->
      ImageIO.write(image, DEFAULT_IMAGE_FORMAT, outputStream)
      imageFile.writeBytes(outputStream.toByteArray())
    }
  }

  internal suspend fun rotateImage(image: BufferedImage, angle: Int): BufferedImage = withContext(Dispatchers.IO) {
    var angle = (angle) % 360
    if (angle < 0) {
      angle += 360
    }
    val width = image.width
    val height = image.height
    val isRightAngle = angle % 180 == 90
    val targetWidth = if (isRightAngle) height else width
    val targetHeight = if (isRightAngle) width else height

    val transform = AffineTransform()
    transform.translate(targetWidth / 2.0, targetHeight / 2.0)
    transform.rotate(Math.toRadians(angle.toDouble()))
    transform.translate(-width / 2.0, -height / 2.0)

    BufferedImage(targetWidth, targetHeight, image.type).also { rotatedImage ->
      rotatedImage.createGraphics().apply {
        this.transform = transform
        drawImage(image, 0, 0, null)
        dispose()
      }
    }
  }

  internal suspend fun applyGrayscale(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY).also { grayscaleImage ->
      grayscaleImage.createGraphics().apply {
        drawImage(image, 0, 0, null)
        dispose()
      }
    }
  }

  internal suspend fun displaySingleChannel(image: BufferedImage, channelIndex: Int): BufferedImage = withContext(Dispatchers.IO) {
    val inRaster = image.raster
    BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY).also { channelImage ->
      val outRaster = channelImage.raster
      for (y in 0 until image.height)
        for (x in 0 until image.width)
          outRaster.setSample(x, y, 0, inRaster.getSample(x, y, channelIndex))
    }
  }

  internal suspend fun applyInvertChannels(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val width = image.width
    val height = image.height
    val numBands = image.raster.numBands
    val pixel = IntArray(numBands)
    val inRaster = image.raster

    BufferedImage(width, height, image.type).also { invertedImage ->
      val outRaster = invertedImage.raster
      for (y in 0 until height) {
        for (x in 0 until width) {
          inRaster.getPixel(x, y, pixel)
          val invertedPixel = IntArray(numBands) { i -> if (numBands == 4 && i == 0) pixel[0] else 255 - pixel[i] }
          outRaster.setPixel(x, y, invertedPixel)
        }
      }
    }
  }

  internal suspend fun applyReverseChannelsOrder(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val width = image.width
    val height = image.height
    val numBands = image.raster.numBands
    val pixel = IntArray(numBands)
    val inRaster = image.raster

    BufferedImage(width, height, image.type).also { reversedImage ->
      val outRaster = reversedImage.raster
      for (y in 0 until height) {
        for (x in 0 until width) {
          inRaster.getPixel(x, y, pixel)
          val reversedPixel = when (numBands) {
            4 -> intArrayOf(pixel[0], pixel[3], pixel[2], pixel[1])
            3 -> intArrayOf(pixel[2], pixel[1], pixel[0])
            else -> pixel
          }
          outRaster.setPixel(x, y, reversedPixel)
        }
      }
    }
  }

  internal suspend fun applyBinarization(image: BufferedImage, threshold: Int): BufferedImage = withContext(Dispatchers.IO) {
    val width = image.width
    val height = image.height
    val inRaster = image.raster
    val numBands = inRaster.numBands
    val pixel = IntArray(numBands)

    BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY).also { binarizedImage ->
      val outRaster = binarizedImage.raster
      for (y in 0 until height) {
        for (x in 0 until width) {
          inRaster.getPixel(x, y, pixel)
          val brightness = when (numBands) {
            4 -> calculateBrightness(pixel[1], pixel[2], pixel[3])
            3 -> calculateBrightness(pixel[0], pixel[1], pixel[2])
            else -> pixel[0]
          }
          val binaryValue = if (brightness < threshold) 0 else 1
          outRaster.setSample(x, y, 0, binaryValue)
        }
      }
    }
  }

  internal suspend fun normalizeImage(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val width = image.width
    val height = image.height
    val numBands = image.raster.numBands
    val pixel = IntArray(numBands)
    val inRaster = image.raster
    val mins = IntArray(numBands) { 255 }
    val maxs = IntArray(numBands) { 0 }
    for (y in 0 until height)
      for (x in 0 until width) {
        inRaster.getPixel(x, y, pixel)
        for (i in 0 until numBands) {
          if (pixel[i] < mins[i]) mins[i] = pixel[i]
          if (pixel[i] > maxs[i]) maxs[i] = pixel[i]
        }
      }

    BufferedImage(width, height, image.type).also { normalizedImage ->
      val outRaster = normalizedImage.raster
      for (y in 0 until height)
        for (x in 0 until width) {
          inRaster.getPixel(x, y, pixel)
          val normalizedPixel = IntArray(numBands) { i ->
            if (numBands == 4 && i == 0) pixel[0] else normalizeChannel(pixel[i], mins[i], maxs[i])
          }
          outRaster.setPixel(x, y, normalizedPixel)
        }
    }
  }

  private fun normalizeChannel(value: Int, min: Int, max: Int): Int =
    if (max > min) ((value - min) * 255 / (max - min)).coerceIn(0, 255) else value

  // Calculate brightness using standard coefficients for RGB to luminance conversion
  // https://www.itu.int/rec/R-REC-BT.709
  private fun calculateBrightness(r: Int, g: Int, b: Int): Int {
    return (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()
  }
}