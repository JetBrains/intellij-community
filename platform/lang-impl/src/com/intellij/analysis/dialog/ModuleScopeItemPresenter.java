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

final class ModuleScopeItemPresenter implements ModelScopeItemPresenter {
  @Override
  public int getScopeId() {
    return AnalysisScope.MODULE;
  }

  @Override
  public @NotNull JRadioButton getButton(ModelScopeItem m) {
    ModuleScopeItem model = (ModuleScopeItem) m;
    JRadioButton button = new JRadioButton();
    button.setText(CodeInsightBundle.message("scope.option.module.with.mnemonic", model.Module.getName()));
    return button;
  }

  @Override
  public @NotNull List<JComponent> getAdditionalComponents(JRadioButton b, ModelScopeItem m, Disposable dialogDisposable) {
    return Collections.emptyList();
  }

  @Override
  public boolean isApplicable(ModelScopeItem model) {
    return model instanceof ModuleScopeItem;
  }

  @Override
  public @Nullable ModelScopeItem tryCreate(@NotNull Project project,
                                            @NotNull AnalysisScope scope,
                                            @Nullable Module module,
                                            @Nullable PsiElement context) {
    return ModuleScopeItem.tryCreate(module);
  }
}