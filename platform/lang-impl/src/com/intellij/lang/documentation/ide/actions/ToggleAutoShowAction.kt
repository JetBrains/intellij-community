// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

internal class ToggleAutoShowAction : ToggleAction(), ActionToIgnore {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val visible = project != null && LookupManager.getInstance(project).activeLookup != null
    e.presentation.isEnabledAndVisible = visible
    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO = state
  }
}
