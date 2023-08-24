// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.formatting.visualLayer.VisualFormattingLayerService.Companion.visualFormattingLayerCodeStyleSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent
import com.intellij.psi.codeStyle.CodeStyleSettingsListener

class VisualFormattingRestartingListener(private val project: Project) : CodeStyleSettingsListener {
  override fun codeStyleSettingsChanged(event: CodeStyleSettingsChangeEvent) {
    val virtualFile = event.virtualFile
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
    val editors = FileEditorManager.getInstance(project).run {
      if (virtualFile != null) getAllEditors(virtualFile) else allEditors
    }
    editors
      .filter {
        it is TextEditor && it.editor.visualFormattingLayerCodeStyleSettings != null && !it.editor.document.isWritable
      }
      .mapNotNull { psiDocumentManager.getCachedPsiFile((it as TextEditor).editor.document) }
      .toSet()
      .forEach(daemonCodeAnalyzer::restart)
  }
}
