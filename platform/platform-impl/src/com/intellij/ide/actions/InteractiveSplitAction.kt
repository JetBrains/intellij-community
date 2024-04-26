// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.OpenFileAction.Companion.openFile
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.SplitterService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry

private class InteractiveSplitAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
                                         && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
                                         && Registry.`is`("ide.open.in.split.with.chooser.enabled")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    var editorWindow = e.getData(EditorWindow.DATA_KEY)
    // When invoked from editor VF in context can be different from the actual editor VF, e.g. for diff in editor tab
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: editorWindow?.selectedFile
    val openedFromEditor = editorWindow != null
    if (!openedFromEditor) {
      editorWindow = FileEditorManagerEx.getInstanceEx(project).splitters.currentWindow
    }
    if (editorWindow == null) {
      // If no editor is currently opened, just open file
      openFile(file!!, project)
    }
    else {
      SplitterService.getInstance(project).activateSplitChooser(editorWindow, file!!, openedFromEditor)
    }
  }

  sealed class Key : AnAction(), ActionRemoteBehaviorSpecification.Frontend, DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    final override fun update(e: AnActionEvent) {
      val project = e.project
      e.presentation.isEnabledAndVisible = ActionPlaces.MAIN_MENU != e.place
                                           && Registry.`is`("ide.open.in.split.with.chooser.enabled")
                                           && project?.serviceIfCreated<SplitterService>()?.isActive == true
    }

    internal class NextWindow : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).nextWindow()
      }
    }

    internal class PreviousWindow : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).previousWindow()
      }
    }

    internal class Exit : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).stopSplitChooser(true)
      }
    }

    internal class Split : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).split(true)
      }
    }

    internal class Duplicate : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).split(false)
      }
    }

    internal class SplitCenter : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).setSplitSide(EditorWindow.RelativePosition.CENTER)
      }
    }

    internal class SplitTop : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).setSplitSide(EditorWindow.RelativePosition.UP)
      }
    }

    internal class SplitLeft : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).setSplitSide(EditorWindow.RelativePosition.LEFT)
      }
    }

    internal class SplitDown : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).setSplitSide(EditorWindow.RelativePosition.DOWN)
      }
    }

    internal class SplitRight : Key() {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SplitterService.getInstance(project).setSplitSide(EditorWindow.RelativePosition.RIGHT)
      }
    }
  }
}