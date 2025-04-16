// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.utils

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Key
import java.awt.image.BufferedImage
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.geom.AffineTransform


object ScientificUtils {
  val SCIENTIFIC_MODE_KEY: Key<Unit> = Key<Unit>("SCIENTIFIC_MODE")
  val ORIGINAL_IMAGE_KEY: Key<BufferedImage> = Key("ORIGINAL_IMAGE")
  val ROTATION_ANGLE_KEY: Key<Int> = Key.create("IMAGE_ROTATION_ANGLE")
  const val DEFAULT_IMAGE_FORMAT: String = "png"

  suspend fun rotateImage(image: BufferedImage, angle: Int): BufferedImage = withContext(Dispatchers.IO) {
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
