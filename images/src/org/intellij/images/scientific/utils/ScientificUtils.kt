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

    val rotatedImage = BufferedImage(targetWidth, targetHeight, image.type)
    val graphics = rotatedImage.createGraphics()
    graphics.transform = transform
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    rotatedImage
  }

  internal suspend fun applyGrayscale(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY).also { grayscaleImage ->
      val graphics = grayscaleImage.createGraphics()
      try {
        graphics.drawImage(image, 0, 0, null)
      } finally {
        graphics.dispose()
      }
    }
  }

  internal suspend fun displaySingleChannel(image: BufferedImage, channelIndex: Int): BufferedImage = withContext(Dispatchers.IO) {
    val raster = image.raster
    BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY).also { channelImage ->
      for (x in 0 until image.width) {
        for (y in 0 until image.height) {
          raster.getSample(x, y, channelIndex).let { value ->
            channelImage.raster.setSample(x, y, 0, value)
          }
        }
      }
    }
  }

  internal suspend fun applyInvertChannels(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    BufferedImage(image.width, image.height, image.type).also { invertedImage ->
      for (x in 0 until image.width) {
        for (y in 0 until image.height) {
          val rgba = image.getRGB(x, y)
          val alpha = rgba ushr 24
          val invertedRgba = (alpha shl 24) or (rgba.inv() and 0xFFFFFF)
          invertedImage.setRGB(x, y, invertedRgba)
        }
      }
    }
  }

  internal suspend fun applyReverseChannelsOrder(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    BufferedImage(image.width, image.height, image.type).also { reversedImage ->
      for (x in 0 until image.width) {
        for (y in 0 until image.height) {
          val rgba = image.getRGB(x, y)
          val alpha = rgba ushr 24
          val blue = rgba and 0xFF
          val green = (rgba shr 8) and 0xFF
          val red = (rgba shr 16) and 0xFF
          val reversedRgba = (alpha shl 24) or (blue shl 16) or (green shl 8) or red
          reversedImage.setRGB(x, y, reversedRgba)
        }
      }
    }
  }

  internal suspend fun applyBinarization(image: BufferedImage, threshold: Int): BufferedImage = withContext(Dispatchers.IO) {
    BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_BINARY).also { binarizedImage ->
      for (y in 0 until image.height) {
        for (x in 0 until image.width) {
          val rgba = image.getRGB(x, y)
          val alpha = rgba ushr 24
          val brightness = calculateBrightness(rgba)
          val binaryColor = if (brightness < threshold) 0x000000 else 0xFFFFFF
          binarizedImage.setRGB(x, y, (alpha shl 24) or binaryColor)
        }
      }
    }
  }

  internal suspend fun normalizeImage(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val (width, height) = image.width to image.height
    var (rMin, rMax) = 255 to 0
    var (gMin, gMax) = 255 to 0
    var (bMin, bMax) = 255 to 0
    for (y in 0 until height) {
      for (x in 0 until width) {
        val rgb = image.getRGB(x, y)
        val (red, green, blue) = Triple(
          (rgb shr 16) and 0xFF,
          (rgb shr 8) and 0xFF,
          rgb and 0xFF
        )
        if (red < rMin) rMin = red else if (red > rMax) rMax = red
        if (green < gMin) gMin = green else if (green > gMax) gMax = green
        if (blue < bMin) bMin = blue else if (blue > bMax) bMax = blue
      }
    }
    val (finalRed, finalGreen, finalBlue) = Triple(rMin to rMax, gMin to gMax, bMin to bMax)
    BufferedImage(width, height, image.type).also { normalizedImage ->
      for (y in 0 until height) {
        for (x in 0 until width) {
          val rgb = image.getRGB(x, y)
          val alpha = rgb and 0xFF000000.toInt()
          val (red, green, blue) = Triple(
            (rgb shr 16) and 0xFF,
            (rgb shr 8) and 0xFF,
            rgb and 0xFF
          )
          val (normalizedRed, normalizedGreen, normalizedBlue) = Triple(
            normalizeChannel(red, finalRed.first, finalRed.second),
            normalizeChannel(green, finalGreen.first, finalGreen.second),
            normalizeChannel(blue, finalBlue.first, finalBlue.second)
          )
          val normalizedARGB = alpha or
            (normalizedRed shl 16) or
            (normalizedGreen shl 8) or
            normalizedBlue
          normalizedImage.setRGB(x, y, normalizedARGB)
        }
      }
    }
  }

  private fun normalizeChannel(value: Int, min: Int, max: Int): Int {
    return if (max > min) ((value - min) * 255 / (max - min)).coerceIn(0, 255) else value
  }

  // Calculate brightness using standard coefficients for RGB to luminance conversion
  // https://www.itu.int/rec/R-REC-BT.709
  private fun calculateBrightness(rgba: Int): Int {
    val red = (rgba shr 16) and 0xFF
    val green = (rgba shr 8) and 0xFF
    val blue = rgba and 0xFF
    return (0.2126 * red + 0.7152 * green + 0.0722 * blue).toInt()
  }
}