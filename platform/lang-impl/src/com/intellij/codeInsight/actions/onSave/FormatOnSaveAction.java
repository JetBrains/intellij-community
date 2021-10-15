// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.actions.*;
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    List<PsiFile> filesToProcessButNotToFormat = new ArrayList<>();
    List<PsiFile> filesToProcessFully = new ArrayList<>();

    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    FormatOnSaveOptions formatOnSaveOptions = FormatOnSaveOptions.getInstance(project);

    for (Document document : documents) {
      PsiFile psiFile = manager.getPsiFile(document);
      if (psiFile == null) continue;

      if (formatOnSaveOptions.isRunOnSaveEnabled() &&
          (formatOnSaveOptions.isAllFileTypesSelected() || formatOnSaveOptions.isFileTypeSelected(psiFile.getFileType()))) {
        filesToProcessFully.add(psiFile);
      }
      else {
        filesToProcessButNotToFormat.add(psiFile);
      }
    }

    boolean onlyChangedLines = VcsFacade.getInstance().hasActiveVcss(project) &&
                               formatOnSaveOptions.isFormatOnlyChangedLines();

    if (!filesToProcessButNotToFormat.isEmpty()) {
      AbstractLayoutCodeProcessor processor =
        createOptimizeRearrangeCleanupProcessor(project, filesToProcessButNotToFormat.toArray(PsiFile.EMPTY_ARRAY), onlyChangedLines, null);
      runProcessor(processor);
    }

    if (!filesToProcessFully.isEmpty()) {
      ReformatCodeProcessor p =
        new ReformatCodeProcessor(project, filesToProcessFully.toArray(PsiFile.EMPTY_ARRAY), null, onlyChangedLines);
      AbstractLayoutCodeProcessor processor =
        createOptimizeRearrangeCleanupProcessor(project, filesToProcessFully.toArray(PsiFile.EMPTY_ARRAY), onlyChangedLines, p);
      runProcessor(processor);
    }
  }

  private static void runProcessor(@Nullable AbstractLayoutCodeProcessor processor) {
    if (processor != null) {
      // This guarantees that per-file undo chain won't break and there won't be the "Following files affected by this action have been already changed" modal error dialog.
      processor.setProcessAllFilesAsSingleUndoStep(false);
      processor.run();
    }
  }

  private static @Nullable AbstractLayoutCodeProcessor createOptimizeRearrangeCleanupProcessor(@NotNull Project project,
                                                                                               @NotNull PsiFile @NotNull [] filesToProcess,
                                                                                               boolean onlyChangedLines,
                                                                                               @Nullable ReformatCodeProcessor reformatProcessor) {
    AbstractLayoutCodeProcessor processor = reformatProcessor;
    if (OptimizeImportsOnSaveOptions.getInstance(project).isRunOnSaveEnabled()) {
      processor = processor != null
                  ? new OptimizeImportsProcessor(processor)
                  : new OptimizeImportsProcessor(project, filesToProcess, null);
    }
    if (RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project)) {
      processor = processor != null
                  ? new RearrangeCodeProcessor(processor)
                  : new RearrangeCodeProcessor(project, filesToProcess, CodeInsightBundle.message("command.rearrange.code"), null,
                                               onlyChangedLines);
    }
    if (CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project) && !DumbService.isDumb(project)) {
      processor = processor != null
                  ? new CodeCleanupCodeProcessor(processor)
                  : new CodeCleanupCodeProcessor(project, filesToProcess, null, onlyChangedLines);
    }
    return processor;
  }
}
