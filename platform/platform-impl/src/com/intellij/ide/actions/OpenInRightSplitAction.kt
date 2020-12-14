// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nullable
import javax.swing.SwingConstants

class OpenInRightSplitAction : AnAction(), DumbAware {
  
  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return
    val file = getVirtualFile(e) ?: return

    val element = e.getData(CommonDataKeys.PSI_ELEMENT) as? Navigatable
    openInRightSplit(project, file, element)
  }
  
  override fun update(e: AnActionEvent) {
    val project = getEventProject(e)
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    } 
    
    val contextFile =  getVirtualFile(e)
    e.presentation.isEnabledAndVisible = contextFile != null
  }

  companion object {
    private fun getVirtualFile(e: AnActionEvent): VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)

    fun openInRightSplit(project: @Nullable Project, file: VirtualFile, element: Navigatable? = null) {
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      val splitters = fileEditorManager.splitters
      
      if (splitters.openInRightSplit(file) == null) {
        element?.navigate(true)
        return
      }

      if (element != null && element !is PsiFile) {
        ApplicationManager.getApplication().invokeLater({ element.navigate(true) }, project.disposed)
      }
    }
  }
}