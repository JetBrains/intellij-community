// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.newToolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction

class RunDebugLabelAction : ToolbarLabelAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isVisible = true
    e.presentation.isEnabled = true
    e.presentation.text = "Run|Debug"
  }
}

