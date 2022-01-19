// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.application.options.RegistryManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction


class ToggleVisualLayerAction : ToggleAction() {

  override fun isSelected(e: AnActionEvent): Boolean = getFacade(e).enabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      getFacade(e).enable()
    }
    else {
      getFacade(e).disable()
    }
  }

  override fun update(e: AnActionEvent) {
    if (!RegistryManager.getInstance().`is`("editor.appearance.visual.formatting.layer.enabled")) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  fun getFacade(e: AnActionEvent): VisualFormattingLayerFacade =
    requireNotNull(e.project).getService(VisualFormattingLayerFacade::class.java)

}
