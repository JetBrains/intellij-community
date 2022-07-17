/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.util.ui.ImageUtil
import java.awt.Color
import java.awt.GraphicsConfiguration
import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

class ImageInverter(foreground: Color, background: Color, private val graphicsConfiguration: GraphicsConfiguration? = null) {
  private val rgb = FloatArray(3)
  private val hsl = FloatArray(3)
  private val whiteHsl = FloatArray(3)
  private val blackHsl = FloatArray(3)

  init {
    foreground.getRGBColorComponents(rgb)
    convertRGBtoHSL(rgb, whiteHsl)
    background.getRGBColorComponents(rgb)
    convertRGBtoHSL(rgb, blackHsl)
  }

  /**
   * Check if [image] should be inverted in dark themes.
   *
   * @param brightnessThreshold images with average brightness exceeding the threshold will be recommended for inversion
   *
   * @return true if it's recommended to invert the image
   */
  fun shouldInvert(image: BufferedImage, brightnessThreshold: Double = 0.7): Boolean {
    val colors = getImageSample(image)
    val numberOfColorsInComplexImage = 5000
    val numberOfPixels = colors.size
    val numberOfColorsThreshold = min(numberOfPixels / 3, numberOfColorsInComplexImage)
    val hasAlpha = image.colorModel.hasAlpha()
    
    val averageBrightness = colors.map { getBrightness(it, hasAlpha) }.sum() / numberOfPixels
    val numberOfColors = colors.toSet()
    
    return (averageBrightness > brightnessThreshold && numberOfColors.size < numberOfColorsThreshold) ||
           hasLightBackground(colors, hasAlpha, brightnessThreshold) == true
  }

  /**
   * Get part of image for color analysis.
   * 
   * For narrow/low images all image pixels are returned.
   * For regular images the result is a concatenation of areas in image corners and at the central area.
   */
  private fun getImageSample(image: BufferedImage): IntArray {
    if (image.height < 10 || image.width < 10) {
      val colors = IntArray(image.height * image.width)
      image.getRGB(0, 0, image.width, image.height, colors, 0, image.width)
      return colors
    }
    
    val defaultSpotSize = min(max(image.height / 10, image.width / 10), min(image.height, image.width))
    val spotHeight = min(image.height, defaultSpotSize)
    val spotWidth = min(image.width, defaultSpotSize)

    val spotSize = spotHeight * spotWidth
    val colors = IntArray(spotSize * 5)

    image.getRGB(0, 0, spotWidth, spotHeight, colors, 0, spotWidth)
    image.getRGB(image.width - spotWidth, 0, spotWidth, spotHeight, colors, spotSize, spotWidth)
    image.getRGB(0, image.height - spotHeight, spotWidth, spotHeight, colors, 2 * spotSize, spotWidth)
    image.getRGB(image.width - spotWidth, image.height - spotHeight, spotWidth, spotHeight, colors, 3 * spotSize, spotWidth)
    
    // We operate on integers so dividing and multiplication with the same number is not trivial operation 
    val centralSpotX = image.width / spotWidth / 2 * spotWidth
    val centralSpotY = image.height / spotHeight / 2 * spotHeight
    
    image.getRGB(centralSpotX, centralSpotY, spotWidth, spotHeight, colors, 4 * spotSize, spotWidth)
    
    return colors
  }
  
  private fun getBrightness(argb: Int, hasAlpha: Boolean): Float {
    val color = Color(argb, hasAlpha)
    val hsb = FloatArray(3)
    Color.RGBtoHSB(color.red, color.green, color.blue, hsb)
    return hsb[2]
  }

  /**
   * Try to guess whether the image has light background.
   * 
   * The background is defined as a large fraction of pixels with the same color.
   */
  private fun hasLightBackground(colors: IntArray, hasAlpha: Boolean, brightnessThreshold: Double): Boolean? {
    val dominantColorPair = colors.groupBy { it }.maxByOrNull { it.value.size } ?: return null
    val dominantColor = dominantColorPair.key
    val dominantPixels = dominantColorPair.value
    
    return dominantPixels.size.toDouble() / colors.size > 0.5 && getBrightness(dominantColor, hasAlpha) > brightnessThreshold
  }
  
  fun invert(color: Color): Color {
    val alpha = invert(color.rgb)
    val argb = convertHSLtoRGB(hsl, alpha)
    return Color(argb, true)
  }

  fun invert(image: BufferedImage): BufferedImage {
    val width = ImageUtil.getUserWidth(image)
    val height = ImageUtil.getUserHeight(image)
    return ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB).also { outputImage ->
      invertInPlace(image, outputImage)
    }
  }

  fun invert(content: ByteArray): ByteArray {
    val image = ImageIO.read(ByteArrayInputStream(content)) ?: return content
    val outputImage = createImageWithInvertedPalette(image)
    invertInPlace(image, outputImage)
    return ByteArrayOutputStream().use { outputStream ->
      ImageIO.write(outputImage, "png", outputStream)
      outputStream.flush()
      outputStream.toByteArray()
    }
  }

  private fun invertInPlace(image: BufferedImage, outputImage: BufferedImage) {
    val rgbArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    if (rgbArray.isEmpty()) return
    // Usually graph data contains regions with same color. Previous converted color may be reused.
    var prevArgb = rgbArray[0]
    var prevConverted = convertHSLtoRGB(hsl, invert(prevArgb))
    for (i in rgbArray.indices) {
      val argb = rgbArray[i]
      if (argb != prevArgb) {
        prevArgb = argb
        prevConverted = convertHSLtoRGB(hsl, invert(argb))
      }
      rgbArray[i] = prevConverted
    }
    outputImage.setRGB(0, 0, image.width, image.height, rgbArray, 0, image.width)
  }

  private fun createImageWithInvertedPalette(image: BufferedImage): BufferedImage {
    val model = image.colorModel
    if (model !is IndexColorModel) {
      return image
    }
    val palette = IntArray(model.mapSize)
    model.getRGBs(palette)
    for ((index, argb) in palette.withIndex()) {
      val alpha = invert(argb)
      palette[index] = convertHSLtoRGB(hsl, alpha)
    }

    return ImageUtil.createImage(graphicsConfiguration, image.width, image.height, BufferedImage.TYPE_BYTE_INDEXED)
  }

  // Note: returns alpha, resulting color resides in `hsl`
  private fun invert(argb: Int): Float {
    val alpha = ((argb shr 24) and 255) / 255f
    rgb[R] = ((argb shr 16) and 255) / 255f
    rgb[G] = ((argb shr 8) and 255) / 255f
    rgb[B] = ((argb) and 255) / 255f
    convertRGBtoHSL(rgb, hsl)
    hsl[SATURATION] = hsl[SATURATION] * (50.0f + whiteHsl[SATURATION]) / 1.5f / 100f
    hsl[LUMINANCE] = (100 - hsl[LUMINANCE]) * (whiteHsl[LUMINANCE] - blackHsl[LUMINANCE]) / 100f + blackHsl[LUMINANCE]
    return alpha
  }

  companion object {
    private const val SATURATION = 1
    private const val LUMINANCE = 2
    private const val R = 0
    private const val G = 1
    private const val B = 2
  }
}
