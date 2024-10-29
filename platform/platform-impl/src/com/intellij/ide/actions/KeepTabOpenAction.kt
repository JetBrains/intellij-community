// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class KeepTabOpenAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val (window, composite) = getActionState(e) ?: return
    composite.isPreview = false
    window.owner.scheduleUpdateFileColor(composite.file)
  }

  override fun update(e: AnActionEvent) {
    val state = getActionState(e)
    if (state == null || !state.composite.isPreview) {
      if (ActionPlaces.isMainMenuOrActionSearch(e.place)) {
        e.presentation.isEnabled = false
      }
      else {
        e.presentation.isEnabledAndVisible = false
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun getActionState(e: AnActionEvent): ActionState? {
    val project = e.project ?: return null
    val fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR) ?: return null
    val virtualFile = fileEditor.file ?: return null
    val editorManager = FileEditorManagerEx.getInstanceEx(project)
    val currentWindow = e.getData(EditorWindow.DATA_KEY) ?: editorManager.currentWindow ?: return null
    val composite = currentWindow.getComposite(virtualFile) ?: return null
    return ActionState(currentWindow, composite)
  }

  private data class ActionState(val window: EditorWindow, val composite: EditorComposite)
}
