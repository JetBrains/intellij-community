// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor
import com.intellij.codeInsight.actions.CodeCleanupCodeProcessor
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInsight.actions.RearrangeCodeProcessor
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FormatOnSaveAction : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {
  override fun isEnabledForProject(project: Project): Boolean {
    return FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled ||
           OptimizeImportsOnSaveOptions.getInstance(project).isRunOnSaveEnabled ||
           RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project) ||
           CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project)
  }

  override val presentableName: String
    get() = ReformatCodeProcessor.getCommandName()

  override suspend fun updateDocument(project: Project, document: Document) {
    val psiFile = readAction {
      PsiDocumentManager.getInstance(project)
        .getPsiFile(document)
        ?.takeIf { LanguageFormatting.INSTANCE.isAutoFormatAllowed(it) }
    } ?: return

    val isFormat = run {
      val formatOptions = FormatOnSaveOptions.getInstance(project)
      formatOptions.isRunOnSaveEnabled && formatOptions.isFileTypeSelected(psiFile.fileType)
    }
    val isOptimizeImports = run {
      val optimizeImportsOptions = OptimizeImportsOnSaveOptions.getInstance(project)
      optimizeImportsOptions.isRunOnSaveEnabled && optimizeImportsOptions.isFileTypeSelected(psiFile.fileType)
    }
    val changedLinesOnly = VcsFacade.getInstance().hasActiveVcss(project) &&
                           FormatOnSaveOptions.getInstance(project).isFormatOnlyChangedLines

    var processor: AbstractLayoutCodeProcessor? = null
    val arrayOfPsiFile = arrayOf(psiFile)
    if (isFormat) {
      processor = ReformatCodeProcessor(project, arrayOfPsiFile, null, changedLinesOnly)
    }
    if (isOptimizeImports) {
      processor = if (processor != null) {
        OptimizeImportsProcessor(processor)
      }
      else {
        OptimizeImportsProcessor(project, arrayOf(psiFile), null)
      }
    }
    if (RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project)) {
      processor = if (processor != null) {
        RearrangeCodeProcessor(processor)
      }
      else {
        RearrangeCodeProcessor(project, arrayOfPsiFile, null, changedLinesOnly)
      }
    }
    if (CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project)) {
      processor = if (processor != null) {
        CodeCleanupCodeProcessor(processor)
      }
      else {
        CodeCleanupCodeProcessor(project, arrayOfPsiFile, null, changedLinesOnly)
      }
      processor.setProfile(CodeCleanupOnSaveOptions.getInstance(project).getInspectionProfile())
    }
    if (processor == null) {
      return
    }
    processor.setProcessAllFilesAsSingleUndoStep(false)

    withContext(Dispatchers.Default) {
      coroutineToIndicator {
        processor.processFilesUnderProgress(it)
      }
    }
  }
}
