// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

internal class ToggleShowDocsInPopupAction : ToggleAction(), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return !DocumentationManager.skipPopup
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    DocumentationManager.skipPopup = !state
  }
}
