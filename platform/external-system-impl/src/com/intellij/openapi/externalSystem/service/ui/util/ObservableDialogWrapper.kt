// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList

@ApiStatus.Internal
abstract class ObservableDialogWrapper(project: Project?) : DialogWrapper(project) {
  private val okButtonListeners = CopyOnWriteArrayList<() -> Unit>()

  fun whenOkButtonPressed(listener: () -> Unit) {
    okButtonListeners.add(listener)
  }

  private fun fireOkButtonPressed() {
    okButtonListeners.forEach { it() }
  }

  protected abstract fun configureCenterPanel(panel: Panel)

  final override fun createCenterPanel() = panel {
    configureCenterPanel(this)

    onApply {
      fireOkButtonPressed()
    }
  }
}