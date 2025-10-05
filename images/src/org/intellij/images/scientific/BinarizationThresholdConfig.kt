// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class BinarizationThresholdConfig {
  companion object {
    private const val THRESHOLD_KEY = "BinarizationThreshold"
    private const val DEFAULT_THRESHOLD = 128

    @JvmStatic
    fun getInstance(): BinarizationThresholdConfig = service()
  }

  private val propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance()
  var threshold: Int
    get() = propertiesComponent.getInt(THRESHOLD_KEY, DEFAULT_THRESHOLD)
    set(value) {
      propertiesComponent.setValue(THRESHOLD_KEY, value.coerceIn(0, 255), DEFAULT_THRESHOLD)
    }
}