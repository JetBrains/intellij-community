// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent
import com.intellij.psi.codeStyle.CodeStyleSettingsListener

class CodeStyleBasedHighlighterRestartingListener(private val project: Project) : CodeStyleSettingsListener {
  override fun codeStyleSettingsChanged(event: CodeStyleSettingsChangeEvent) {
    val file = event.virtualFile?.findPsiFile(project)
    if (file != null) {
      DaemonCodeAnalyzer.getInstance(project).restart(file)
    }
    else {
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }
}