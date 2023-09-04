// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.tree

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction

class TreeSpeedSearchAction : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val contextComponent = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return
    e.presentation.isEnabled = contextComponent.getSpeedSearchActionHandler()?.isSpeedSearchActive() == false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val contextComponent = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return
    val handler = contextComponent.getSpeedSearchActionHandler() ?: return
    handler.activateSpeedSearch()
  }

}
