// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class FormatOnSaveAction extends ActionsOnSaveFileDocumentManagerListener.ActionOnSave {
  @Override
  public boolean isEnabledForProject(@NotNull Project project) {
    return FormatOnSaveActionInfo.isReformatOnSaveEnabled(project);
  }

  @Override
  public void processDocuments(@NotNull Project project, @NotNull Document @NotNull [] documents) {
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    PsiFile[] files = ContainerUtil.mapNotNull(documents, d -> manager.getPsiFile(d)).toArray(PsiFile.EMPTY_ARRAY);
    if (files.length == 0) {
      return;
    }

    boolean onlyChangedLines = FormatOnSaveActionInfo.isReformatOnlyChangedLinesOnSave(project);
    new ReformatCodeProcessor(project, files, null, onlyChangedLines).run();
  }
}
