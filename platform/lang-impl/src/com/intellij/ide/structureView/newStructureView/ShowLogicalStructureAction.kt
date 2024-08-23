// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.ide.impl.StructureViewWrapperImpl
import com.intellij.ide.structureView.StructureViewBundle
import com.intellij.ide.structureView.StructureViewState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager

internal class ShowLogicalStructureAction : ToggleAction(StructureViewBundle.message("structureview.action.show.logical")) {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return StructureViewState.getInstance(project).showLogicalStructure
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    StructureViewState.getInstance(project).showLogicalStructure = state
    ApplicationManager.getApplication().messageBus.syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run()
  }
}