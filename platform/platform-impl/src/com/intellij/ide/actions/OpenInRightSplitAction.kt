// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nullable

class OpenInRightSplitAction : AnAction(), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return
    val file = getVirtualFile(e) ?: return


    val element = e.getData(CommonDataKeys.PSI_ELEMENT) as? Navigatable
    val editorWindow = openInRightSplit(project, file, element)
    if (element == null && editorWindow != null) {
      val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
      if (files.size > 1) {
        files.forEach {
          if (file == it) return@forEach
          val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
          fileEditorManager.openFileWithProviders(it, true, editorWindow)
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val project = getEventProject(e)
    val editor = e.getData(CommonDataKeys.EDITOR)

    val place = e.place
    if (project == null ||
        editor != null ||
        place == ActionPlaces.EDITOR_TAB_POPUP ||
        place == ActionPlaces.EDITOR_POPUP) {
      e.presentation.isEnabledAndVisible = false
      return
    }


    val contextFile = getVirtualFile(e)
    e.presentation.isEnabledAndVisible = contextFile != null && !contextFile.isDirectory
  }

  companion object {
    private fun getVirtualFile(e: AnActionEvent): VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)

    fun openInRightSplit(project: @Nullable Project, file: VirtualFile, element: Navigatable? = null): EditorWindow? {
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      val splitters = fileEditorManager.splitters

      val providers = FileEditorProviderManager.getInstance().getProviders(project, file)
      if (providers.isEmpty()) {
        element?.navigate(true)
        return null
      }

      val editorWindow = splitters.openInRightSplit(file)
      if (editorWindow == null) {
        element?.navigate(true)
        return null
      }

      if (element != null && element !is PsiFile) {
        ApplicationManager.getApplication().invokeLater({ element.navigate(true) }, project.disposed)
      }
      return editorWindow
    }
  }
}