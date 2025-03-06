// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Key
import java.awt.image.BufferedImage
import com.intellij.openapi.components.State


object ScientificUtils {
  val SCIENTIFIC_MODE_KEY: Key<Unit> = Key<Unit>("SCIENTIFIC_MODE")
  val ORIGINAL_IMAGE_KEY: Key<BufferedImage> = Key("ORIGINAL_IMAGE")
  const val DEFAULT_IMAGE_FORMAT: String = "png"
}


@State(name = "BinarizationThresholdConfig", storages = [Storage("binarizationThresholdConfig.xml")])
object BinarizationThresholdConfig : PersistentStateComponent<BinarizationThresholdConfig> {

  private const val DEFAULT_THRESHOLD = 128

  var threshold: Int = DEFAULT_THRESHOLD

  override fun getState(): BinarizationThresholdConfig {
    return this
  }

  override fun loadState(state: BinarizationThresholdConfig) {
    this.threshold = state.threshold
  }
}
