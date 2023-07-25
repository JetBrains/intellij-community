// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.dialog;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

final class ProjectScopeItemPresenter implements ModelScopeItemPresenter {
  @Override
  public int getScopeId() {
    return AnalysisScope.PROJECT;
  }

  @Override
  public @NotNull JRadioButton getButton(ModelScopeItem m) {
    String message = CodeInsightBundle.message("scope.option.whole.project");
    JRadioButton button = new JRadioButton();
    button.setText(message);
    return button;
  }

  @Override
  public @NotNull List<JComponent> getAdditionalComponents(JRadioButton button, ModelScopeItem model, Disposable dialogDisposable) {
    return Collections.emptyList();
  }

  @Override
  public boolean isApplicable(ModelScopeItem model) {
    return model instanceof ProjectScopeItem;
  }

  @Override
  public @NotNull ModelScopeItem tryCreate(@NotNull Project project,
                                           @NotNull AnalysisScope scope,
                                           @Nullable Module module,
                                           @Nullable PsiElement context) {
    return new ProjectScopeItem(project);
  }
}