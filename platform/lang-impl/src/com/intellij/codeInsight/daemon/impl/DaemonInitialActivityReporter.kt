// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DaemonInitialActivityReporter(private val project: Project, val coroutineScope: CoroutineScope) : DaemonCodeAnalyzer.DaemonListener {
  @Volatile
  private var initialEntireFileHighlightingActivity: Activity? = null
  @Volatile
  private var initialEntireFileHighlightingReported: Boolean = false

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    if (!initialEntireFileHighlightingReported) {
      val fileEditor = fileEditors.asSequence().filterIsInstance<TextEditor>().firstOrNull() ?: return
      val editor = fileEditor.editor
      if (editor.editorKind != EditorKind.MAIN_EDITOR && !ApplicationManager.getApplication().isUnitTestMode) {
        return
      }
      initialEntireFileHighlightingActivity = StartUpMeasurer.startActivity("initial entire file highlighting")
    }
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    if (!initialEntireFileHighlightingReported) {
      coroutineScope.launch {
        val fileEditor = fileEditors.filterIsInstance<TextEditor>().firstOrNull() ?: return@launch
        val editor = fileEditor.editor
        if (editor.editorKind != EditorKind.MAIN_EDITOR && !ApplicationManager.getApplication().isUnitTestMode) return@launch
        if (!fileEditor.isValid || project.isDisposed) return@launch
        val highlightingCompleted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(fileEditor, project)

        if (highlightingCompleted) {
          initialEntireFileHighlightingReported = true
          initialEntireFileHighlightingActivity!!.end()
          initialEntireFileHighlightingActivity = null
          StartUpMeasurer.addInstantEvent("editor highlighting completed")
          StartUpPerformanceService.getInstance().editorRestoringTillHighlighted()
        }
      }
    }
  }
}