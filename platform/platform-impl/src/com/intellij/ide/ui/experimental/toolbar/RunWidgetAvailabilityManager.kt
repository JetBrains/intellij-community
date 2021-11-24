// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.experimental.toolbar

import com.intellij.openapi.project.Project

abstract class RunWidgetAvailabilityManager {
  companion object{
    fun getInstance(project: Project): RunWidgetAvailabilityManager = project.getService(RunWidgetAvailabilityManager::class.java)
  }

  private val listeners = mutableListOf<RunWidgetAvailabilityListener>()

  fun addListener(listener: RunWidgetAvailabilityListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: RunWidgetAvailabilityListener) {
    listeners.remove(listener)
  }

  abstract fun isAvailable(): Boolean

  protected fun fireUpdate(value: Boolean) {
    listeners.forEach { it.availabilityChanged(value) }
  }

  @FunctionalInterface
  fun interface RunWidgetAvailabilityListener {

    fun availabilityChanged(value: Boolean)
  }
}

internal class BaseRunWidgetAvailabilityManager : RunWidgetAvailabilityManager() {

  override fun isAvailable(): Boolean = true

}