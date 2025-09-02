// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave

import com.intellij.codeInsight.actions.*
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.SlowOperations
import java.util.concurrent.FutureTask

internal class FormatOnSaveAction : ActionOnSave() {
  override fun isEnabledForProject(project: Project): Boolean {
    return FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled ||
           OptimizeImportsOnSaveOptions.getInstance(project).isRunOnSaveEnabled ||
           RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project) ||
           CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project)
  }

  override fun processDocuments(project: Project, documents: Array<Document>) {
    val allFiles = mutableListOf<PsiFile>()
    val filesToFormat = mutableListOf<PsiFile>()
    val filesToOptimizeImports = mutableListOf<PsiFile>()

    val manager = PsiDocumentManager.getInstance(project)
    val formatOptions = FormatOnSaveOptions.getInstance(project)
    val optimizeImportsOptions = OptimizeImportsOnSaveOptions.getInstance(project)

    for (document in documents) {
      val psiFile = SlowOperations.knownIssue("IJPL-162973").use {
        manager.getPsiFile(document)?.takeIf {
          LanguageFormatting.INSTANCE.isAutoFormatAllowed(it)
        }
      }
      if (psiFile == null) continue

      allFiles.add(psiFile)

      if (formatOptions.isRunOnSaveEnabled && formatOptions.isFileTypeSelected(psiFile.fileType)) {
        filesToFormat.add(psiFile)
      }

      if (optimizeImportsOptions.isRunOnSaveEnabled && optimizeImportsOptions.isFileTypeSelected(psiFile.fileType)) {
        filesToOptimizeImports.add(psiFile)
      }
    }

    if (filesToFormat.isEmpty() &&
        filesToOptimizeImports.isEmpty() &&
        !RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project) &&
        !CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project)
    ) {
      // nothing to do
      return
    }

    processFiles(project, allFiles, filesToFormat, filesToOptimizeImports)
  }

  private fun processFiles(project: Project,
                           allFiles: List<PsiFile>,
                           filesToFormat: List<PsiFile>,
                           filesToOptimizeImports: List<PsiFile>) {
    val onlyChangedLines = VcsFacade.getInstance().hasActiveVcss(project) &&
                           FormatOnSaveOptions.getInstance(project).isFormatOnlyChangedLines

    var processor: AbstractLayoutCodeProcessor =
      object : ReformatCodeProcessor(project, allFiles.toTypedArray(), null, onlyChangedLines) {
        override fun prepareTask(psiFile: PsiFile, processChangedTextOnly: Boolean): FutureTask<Boolean> {
          return if (filesToFormat.contains(psiFile)) super.prepareTask(psiFile, processChangedTextOnly) else emptyTask()
        }
      }

    if (OptimizeImportsOnSaveOptions.getInstance(project).isRunOnSaveEnabled) {
      processor = object : OptimizeImportsProcessor(processor) {
        override fun prepareTask(psiFile: PsiFile, processChangedTextOnly: Boolean): FutureTask<Boolean> {
          return if (filesToOptimizeImports.contains(psiFile)) super.prepareTask(psiFile, processChangedTextOnly) else emptyTask()
        }
      }
    }

    if (RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project)) {
      processor = RearrangeCodeProcessor(processor)
    }

    if (CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project) && !isDumb(project)) {
      processor = CodeCleanupCodeProcessor(processor)
      processor.setProfile(CodeCleanupOnSaveOptions.getInstance(project).getInspectionProfile())
    }

    // This guarantees that per-file undo chain won't break and there won't be the "Following files affected by this action have been already changed" modal error dialog.
    processor.setProcessAllFilesAsSingleUndoStep(false)
    processor.run()
  }
}
