// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.registry.RegistryManager

class ToggleEssentialHighlightingAction : ToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return RegistryManager.getInstance().`is`("ide.highlighting.mode.essential")
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    RegistryManager.getInstance().get("ide.highlighting.mode.essential").setValue(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}