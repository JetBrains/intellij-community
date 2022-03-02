// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.actions.*;
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

public class FormatOnSaveAction extends ActionsOnSaveFileDocumentManagerListener.ActionOnSave {
  @Override
  public boolean isEnabledForProject(@NotNull Project project) {
    return FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled() ||
           OptimizeImportsOnSaveOptions.getInstance(project).isRunOnSaveEnabled() ||
           RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project) ||
           CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project);
  }

  @Override
  public void processDocuments(@NotNull Project project, @NotNull Document @NotNull [] documents) {
    List<PsiFile> allFiles = new ArrayList<>();
    List<PsiFile> filesToFormat = new ArrayList<>();
    List<PsiFile> filesToOptimizeImports = new ArrayList<>();

    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    FormatOnSaveOptions formatOptions = FormatOnSaveOptions.getInstance(project);
    OptimizeImportsOnSaveOptions optimizeImportsOptions = OptimizeImportsOnSaveOptions.getInstance(project);

    for (Document document : documents) {
      PsiFile psiFile = manager.getPsiFile(document);
      if (psiFile == null || !LanguageFormatting.INSTANCE.isAutoFormatAllowed(psiFile)) continue;

      allFiles.add(psiFile);

      if (formatOptions.isRunOnSaveEnabled() &&
          (formatOptions.isAllFileTypesSelected() || formatOptions.isFileTypeSelected(psiFile.getFileType()))) {
        filesToFormat.add(psiFile);
      }

      if (optimizeImportsOptions.isRunOnSaveEnabled() &&
          (optimizeImportsOptions.isAllFileTypesSelected() || optimizeImportsOptions.isFileTypeSelected(psiFile.getFileType()))) {
        filesToOptimizeImports.add(psiFile);
      }
    }

    if (filesToFormat.isEmpty() &&
        filesToOptimizeImports.isEmpty() &&
        !RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project) &&
        !CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project)) {
      // nothing to do
      return;
    }

    procesFiles(project, allFiles, filesToFormat, filesToOptimizeImports);
  }

  private void procesFiles(@NotNull Project project,
                           @NotNull List<PsiFile> allFiles,
                           @NotNull List<PsiFile> filesToFormat,
                           @NotNull List<PsiFile> filesToOptimizeImports) {
    boolean onlyChangedLines = VcsFacade.getInstance().hasActiveVcss(project) &&
                               FormatOnSaveOptions.getInstance(project).isFormatOnlyChangedLines();

    AbstractLayoutCodeProcessor processor =
      new ReformatCodeProcessor(project, allFiles.toArray(PsiFile.EMPTY_ARRAY), null, onlyChangedLines) {
        @Override
        protected @NotNull FutureTask<Boolean> prepareTask(@NotNull PsiFile file, boolean processChangedTextOnly) {
          return filesToFormat.contains(file) ? super.prepareTask(file, processChangedTextOnly) : emptyTask();
        }
      };

    if (OptimizeImportsOnSaveOptions.getInstance(project).isRunOnSaveEnabled()) {
      processor = new OptimizeImportsProcessor(processor) {
        @Override
        protected @NotNull FutureTask<Boolean> prepareTask(@NotNull PsiFile file, boolean processChangedTextOnly) {
          return filesToOptimizeImports.contains(file) ? super.prepareTask(file, processChangedTextOnly) : emptyTask();
        }
      };
    }

    if (RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project)) {
      processor = new RearrangeCodeProcessor(processor);
    }

    if (CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project) && !DumbService.isDumb(project)) {
      processor = new CodeCleanupCodeProcessor(processor);
    }

    // This guarantees that per-file undo chain won't break and there won't be the "Following files affected by this action have been already changed" modal error dialog.
    processor.setProcessAllFilesAsSingleUndoStep(false);
    processor.run();
  }
}
