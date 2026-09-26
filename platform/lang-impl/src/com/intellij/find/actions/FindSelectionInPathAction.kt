// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.util.EditorUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FindSelectionInPathAction : FindInPathAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    e.presentation.isEnabledAndVisible = project != null && !LightEdit.owns(project) && editor != null &&
                                         editor.selectionModel.hasSelection() &&
                                         !EditorUtil.contextMenuInvokedOutsideOfSelection(e)
  }
}
