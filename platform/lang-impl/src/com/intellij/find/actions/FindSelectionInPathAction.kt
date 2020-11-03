// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class FindSelectionInPathAction : FindInPathAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    if (project == null || LightEdit.owns(project) || editor == null || !editor.selectionModel.hasSelection()) {
      e.presentation.isEnabledAndVisible = false
    }
  }
}
