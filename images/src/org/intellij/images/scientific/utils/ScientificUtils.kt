// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.utils

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Key
import java.awt.image.BufferedImage
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service


object ScientificUtils {
  val SCIENTIFIC_MODE_KEY: Key<Unit> = Key<Unit>("SCIENTIFIC_MODE")
  val ORIGINAL_IMAGE_KEY: Key<BufferedImage> = Key("ORIGINAL_IMAGE")
  const val DEFAULT_IMAGE_FORMAT: String = "png"
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
