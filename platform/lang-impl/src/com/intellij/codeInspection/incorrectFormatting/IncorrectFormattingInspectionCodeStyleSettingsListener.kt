// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent
import com.intellij.psi.codeStyle.CodeStyleSettingsListener
import com.intellij.util.FileContentUtilCore

internal class IncorrectFormattingInspectionCodeStyleSettingsListener(val project: Project) : CodeStyleSettingsListener {
  override fun codeStyleSettingsChanged(event: CodeStyleSettingsChangeEvent) {
    ApplicationManager.getApplication().invokeLater {
      val file = event.psiFile
      if (file != null) {
        FileContentUtilCore.reparseFiles(file.viewProvider.virtualFile)
      }
      else {
        FileContentUtilCore.reparseFiles(EditorFactory.getInstance().allEditors.asList()
                                           .filter { it.project != null && it.project == project }
                                           .mapNotNull { FileDocumentManager.getInstance().getFile(it.document) })
      }
    }
  }
}