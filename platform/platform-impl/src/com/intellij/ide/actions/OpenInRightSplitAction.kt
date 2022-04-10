// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import javax.swing.JComponent

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
    val fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR)

    val place = e.place
    if (project == null ||
        fileEditor != null ||
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

    @JvmOverloads
    fun openInRightSplit(project: Project, file: VirtualFile, element: Navigatable? = null, requestFocus: Boolean = true): EditorWindow? {
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      val splitters = fileEditorManager.splitters

      if (!fileEditorManager.canOpenFile(file)) {
        element?.navigate(requestFocus)
        return null
      }

      val editorWindow = splitters.openInRightSplit(file, requestFocus)
      if (editorWindow == null) {
        element?.navigate(requestFocus)
        return null
      }

      if (element != null && element !is PsiFile) {
        ApplicationManager.getApplication().invokeLater({ element.navigate(requestFocus) }, project.disposed)
      }
      return editorWindow
    }

    fun overrideDoubleClickWithOneClick(component: JComponent) {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT) ?: return

      val set = action.shortcutSet
      for (shortcut in set.shortcuts) {
        if (shortcut is MouseShortcut) {
          //convert double click -> one click
          if (shortcut.clickCount == 2) {
            val customSet = CustomShortcutSet(MouseShortcut(shortcut.button, shortcut.modifiers, 1))
            object: AnAction(null as String?) {
              override fun actionPerformed(e: AnActionEvent) {
                action.actionPerformed(e)
              }
            }.registerCustomShortcutSet(customSet, component)
          }
        }
      }
    }
  }
}