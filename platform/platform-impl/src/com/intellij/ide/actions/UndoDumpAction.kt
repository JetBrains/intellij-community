// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal


@Experimental
@Internal
open class UndoDumpAction : DumbAwareAction(), ActionRemoteBehaviorSpecification {

  companion object {
    private val LOG: Logger = logger<UndoDumpAction>()
  }

  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(true)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val dataContext: DataContext = event.dataContext
    val editor: FileEditor? = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)
    val undoManager = UndoRedoAction.getUndoManager(editor, dataContext, false, false)
    LOG.warn("__________________________________________________________________________")
    LOG.warn("${undoManager ?: "null undo manager"}")
    if (undoManager is UndoManagerImpl) {
      LOG.warn(undoManager.dumpState(editor))
    }
    LOG.warn("__________________________________________________________________________")
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun getBehavior(): ActionRemoteBehavior {
    return ActionRemoteBehavior.BackendOnly
  }
}
