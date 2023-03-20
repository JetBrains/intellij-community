// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.OpenFileAction.Companion.openFile
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.SplitterService
import com.intellij.openapi.project.DumbAware

private class InteractiveSplitAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    var editorWindow = e.getData(EditorWindow.DATA_KEY)
    // When invoked from editor VF in context can be different from the actual editor VF, e.g. for diff in editor tab
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: editorWindow?.selectedFile
    val openedFromEditor = editorWindow != null
    if (!openedFromEditor) {
      editorWindow = FileEditorManagerEx.getInstanceEx(e.project!!).splitters.currentWindow
    }
    if (editorWindow == null) {
      // If no editor is currently opened, just open file
      openFile(file = file!!, project = e.project!!)
    }
    else {
      SplitterService.getInstance().activateSplitChooser(window = editorWindow, file = file!!, openedFromEditor = openedFromEditor)
    }
  }

  sealed class Key : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      val splitterService = ApplicationManager.getApplication().serviceIfCreated<SplitterService>()
      e.presentation.isEnabledAndVisible = ActionPlaces.MAIN_MENU != e.place && splitterService != null && splitterService.isActive
    }

    internal class NextWindow : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().nextWindow()
      }
    }

    internal class PreviousWindow : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().previousWindow()
      }
    }

    internal class Exit : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().stopSplitChooser(true)
      }
    }

    internal class Split : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().split(true)
      }
    }

    internal class Duplicate : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().split(false)
      }
    }

    internal class SplitCenter : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.CENTER)
      }
    }

    internal class SplitTop : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.UP)
      }
    }

    internal class SplitLeft : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.LEFT)
      }
    }

    internal class SplitDown : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.DOWN)
      }
    }

    internal class SplitRight : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        SplitterService.getInstance().setSplitSide(EditorWindow.RelativePosition.RIGHT)
      }
    }
  }
}