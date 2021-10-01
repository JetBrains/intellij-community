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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class FormatOnSaveAction extends ActionsOnSaveFileDocumentManagerListener.ActionOnSave {
  @Override
  public boolean isEnabledForProject(@NotNull Project project) {
    return FormatOnSaveActionInfo.isReformatOnSaveEnabled(project) ||
           OptimizeImportsOnSaveActionInfo.isOptimizeImportsOnSaveEnabled(project) ||
           RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project) ||
           CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project);
  }

  @Override
  public void processDocuments(@NotNull Project project, @NotNull Document @NotNull [] documents) {
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    PsiFile[] files = ContainerUtil.mapNotNull(documents, d -> manager.getPsiFile(d)).toArray(PsiFile.EMPTY_ARRAY);
    if (files.length == 0) {
      return;
    }

    boolean onlyChangedLines = FormatOnSaveActionInfo.isReformatOnlyChangedLinesOnSave(project);

    AbstractLayoutCodeProcessor processor = null;
    if (FormatOnSaveActionInfo.isReformatOnSaveEnabled(project)) {
      processor = new ReformatCodeProcessor(project, files, null, onlyChangedLines);
    }
    if (OptimizeImportsOnSaveActionInfo.isOptimizeImportsOnSaveEnabled(project)) {
      processor = processor != null
                  ? new OptimizeImportsProcessor(processor)
                  : new OptimizeImportsProcessor(project, files, null);
    }
    if (RearrangeCodeOnSaveActionInfo.isRearrangeCodeOnSaveEnabled(project)) {
      processor = processor != null
                  ? new RearrangeCodeProcessor(processor)
                  : new RearrangeCodeProcessor(project, files, CodeInsightBundle.message("command.rearrange.code"), null, onlyChangedLines);
    }
    if (CodeCleanupOnSaveActionInfo.isCodeCleanupOnSaveEnabled(project) && !DumbService.isDumb(project)) {
      processor = processor != null
                  ? new CodeCleanupCodeProcessor(processor)
                  : new CodeCleanupCodeProcessor(project, files, null, onlyChangedLines);
    }

    if (processor != null) {
      // This guarantees that per-file undo chain won't break and there won't be the "Following files affected by this action have been already changed" modal error dialog.
      processor.setProcessAllFilesAsSingleUndoStep(false);
      processor.run();
    }
  }
}
