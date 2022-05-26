// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.Editor


class ToggleVisualLayerAction : ToggleAction() {

  val service: VisualFormattingLayerService by lazy { VisualFormattingLayerService.getInstance() }

  override fun isSelected(e: AnActionEvent): Boolean =
    getEditor(e)?.let { service.enabledForEditor(it) } ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getEditor(e)?.let { editor ->
      if (state) {
        service.enableForEditor(editor)
      }
      else {
        service.disableForEditor(editor)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    if (!service.enabledByRegistry) {
      e.presentation.isEnabledAndVisible = false
    }
    super.update(e)
  }

  private fun getEditor(e: AnActionEvent): Editor? {
    return e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)
  }

}
