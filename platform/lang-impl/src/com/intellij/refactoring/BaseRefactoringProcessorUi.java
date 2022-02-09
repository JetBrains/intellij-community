// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.model.ModelPatch;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BaseRefactoringProcessorUi {
  public void displayPreview(Project project, ModelPatch patch) throws ProcessCanceledException {
    JComponent preview = VcsFacade.getInstance().createPatchPreviewComponent(project, patch);
    if (preview != null) {
      DialogBuilder builder = new DialogBuilder(project).title(RefactoringBundle.message("usageView.tabText")).centerPanel(preview);
      if (builder.show() != DialogWrapper.OK_EXIT_CODE) {
        throw new ProcessCanceledException();
      }
    }
  }

  public ConflictsDialog createConflictsDialog(@NotNull Project project,
                                                  @NotNull MultiMap<PsiElement, String> conflicts,
                                                  @Nullable Runnable doRefactoringRunnable,
                                                  boolean alwaysShowOkButton,
                                                  boolean canShowConflictsInView) {
    return new ConflictsDialog(project, conflicts, doRefactoringRunnable, alwaysShowOkButton, canShowConflictsInView);
  }
}