// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction

class RDCLabelAction : ToolbarLabelAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val active = e.project?.let {
      RunDebugConfigManager.getInstance(it)?.getState()
    }?.let {
      if (it != RunDebugConfigManager.State.DEFAULT) {
        e.presentation.text = it.toString()
        true
      } else false
    } ?: false

    e.presentation.isEnabledAndVisible = active

  }
}

