// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.model.ModelPatch;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

final class BaseRefactoringProcessorUi {

  ConflictsDialog createConflictsDialog(@NotNull Project project,
                                        @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                        @Nullable Runnable doRefactoringRunnable,
                                        boolean alwaysShowOkButton,
                                        boolean canShowConflictsInView) {
    return new ConflictsDialog(project, conflicts, doRefactoringRunnable, alwaysShowOkButton, canShowConflictsInView);
  }
}