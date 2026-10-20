// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object InlayHintsRefresher {
  @JvmStatic
  @RequiresEdt
  fun refreshAll(project: Project, @NonNls reason: String) {
    for (textEditor in projectTextEditors(project)) {
      resetInlayStamps(textEditor.editor)
    }
    project.service<CodeVisionHost>()
      .invalidateProvider(CodeVisionHost.LensInvalidateSignal(null))
    InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    restartDaemon(psiFile = null, project, reason)
  }

  @JvmStatic
  @RequiresEdt
  fun refreshEditor(editor: Editor, @NonNls reason: String) {
    resetInlayStamps(editor)
    InlayHintsPassFactoryInternal.clearModificationStamp(editor)
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass(editor)
    editor.project?.let {
      it.service<CodeVisionHost>()
        .invalidateProvider(CodeVisionHost.LensInvalidateSignal(editor))
      val psiFile = runReadActionBlocking {
        PsiDocumentManager.getInstance(it).getPsiFile(editor.document)
      }
      restartDaemon(psiFile, it, reason)
    }
  }

  private fun projectTextEditors(project: Project): List<TextEditor> =
    FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>()

  private fun resetInlayStamps(editor: Editor) {
    ModificationStampUtil.clearModificationStamp(editor)
    DeclarativeInlayHintsPassFactory.resetModificationStamp(editor)
  }

  private fun restartDaemonImmediately(restart: () -> Unit) {
    DaemonCodeAnalyzerSettings.getInstance().forceUseZeroAutoReparseDelayIn(ThrowableRunnable { restart() })
  }

  private fun restartDaemon(psiFile: PsiFile?, project: Project, reason: String) {
    val daemon = DaemonCodeAnalyzerEx.getInstanceEx(project)
    restartDaemonImmediately {
      if (psiFile != null) daemon.restart(psiFile, reason) else daemon.restart(reason)
    }
  }
}
