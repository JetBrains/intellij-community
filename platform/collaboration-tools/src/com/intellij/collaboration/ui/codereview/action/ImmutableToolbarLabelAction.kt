// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import org.jetbrains.annotations.Nls

class ImmutableToolbarLabelAction(text: @Nls String) : ToolbarLabelAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  init {
    templatePresentation.text = text
  }
}