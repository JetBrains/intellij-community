// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.utils

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Key
import java.awt.image.BufferedImage
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.geom.AffineTransform
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


object ScientificUtils {
  @JvmField
  var SCIENTIFIC_MODE_KEY: Key<Unit> = Key<Unit>("SCIENTIFIC_MODE")
  val ORIGINAL_IMAGE_KEY: Key<BufferedImage> = Key("ORIGINAL_IMAGE")
  val DATA_TYPE_KEY: Key<String> = Key("DATA_TYPE")
  val ROTATION_ANGLE_KEY: Key<Int> = Key.create("IMAGE_ROTATION_ANGLE")
  val CURRENT_NOT_NORMALIZED_IMAGE_KEY: Key<BufferedImage> = Key("CURRENT_NOT_NORMALIZED_IMAGE")
  val NORMALIZATION_APPLIED_KEY: Key<Boolean> = Key<Boolean>("NORMALIZATION_APPLIED")
  const val DEFAULT_IMAGE_FORMAT: String = "png"

  internal suspend fun saveImageToFile(imageFile: VirtualFile, image: BufferedImage): Unit = withContext(Dispatchers.IO) {
    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(image, DEFAULT_IMAGE_FORMAT, byteArrayOutputStream)
    imageFile.writeBytes(byteArrayOutputStream.toByteArray())
  }

  internal suspend fun rotateImage(image: BufferedImage, angle: Int): BufferedImage = withContext(Dispatchers.IO) {
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

  internal suspend fun normalizeImage(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    val width = image.width
    val height = image.height
    val normalizedImage = BufferedImage(width, height, image.type)

    val red = IntArray(width * height)
    val green = IntArray(width * height)
    val blue = IntArray(width * height)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val rgb = image.getRGB(x, y)
        val i = y * width + x
        red[i] = (rgb shr 16) and 0xFF
        green[i] = (rgb shr 8) and 0xFF
        blue[i] = rgb and 0xFF
      }
    }

    val rMinMax = findMinMax(red)
    val gMinMax = findMinMax(green)
    val bMinMax = findMinMax(blue)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val i = y * width + x
        val alpha = image.getRGB(x, y) and 0xFF000000.toInt()
        val normalizedR = normalizeChannel(red[i], rMinMax.first, rMinMax.second)
        val normalizedG = normalizeChannel(green[i], gMinMax.first, gMinMax.second)
        val normalizedB = normalizeChannel(blue[i], bMinMax.first, bMinMax.second)

        val normalizedARGB = alpha or (normalizedR shl 16) or (normalizedG shl 8) or normalizedB
        normalizedImage.setRGB(x, y, normalizedARGB)
      }
    }

    normalizedImage
  }

  private fun findMinMax(array: IntArray): Pair<Int, Int> {
    var min = array[0]
    var max = array[0]
    for (value in array) {
      if (value < min) min = value
      if (value > max) max = value
    }
    return Pair(min, max)
  }

  private fun normalizeChannel(value: Int, min: Int, max: Int): Int {
    return if (max > min) {
      ((value - min).toFloat() * 255 / (max - min)).toInt().coerceIn(0, 255)
    } else {
      value
    }
  }
}

@State(name = "BinarizationThresholdConfig", storages = [Storage("binarizationThresholdConfig.xml")])
@Service(Service.Level.APP)
class BinarizationThresholdConfig : PersistentStateComponent<BinarizationThresholdConfig.State> {
  companion object {
    private const val DEFAULT_THRESHOLD = 128

    @JvmStatic
    fun getInstance(): BinarizationThresholdConfig {
      return service()
    }
  }

  data class State(var threshold: Int = DEFAULT_THRESHOLD)

  private var state: State = State()

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state
  }

  var threshold: Int
    get() = state.threshold
    set(value) {
      state.threshold = value
    }
}
