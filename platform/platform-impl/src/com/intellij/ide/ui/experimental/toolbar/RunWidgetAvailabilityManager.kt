// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.toolbar

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class RunWidgetAvailabilityManager {
  companion object {
    fun getInstance(project: Project): RunWidgetAvailabilityManager = project.service()
  }

  private val availabilityChangedMutable = MutableStateFlow(true)
  val availabilityChanged: StateFlow<Boolean>
    get() = availabilityChangedMutable

  // used by Rider
  @Suppress("unused")
  protected fun fireUpdate(value: Boolean) {
    availabilityChangedMutable.value = value
  }

  fun isAvailable(): Boolean = availabilityChangedMutable.value
}