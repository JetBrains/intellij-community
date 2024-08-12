// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.dialog;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class CustomScopeItemPresenter implements ModelScopeItemPresenter {

  @Override
  public int getScopeId() {
    return AnalysisScope.CUSTOM;
  }

  @Override
  public @NotNull JRadioButton getButton(ModelScopeItem model) {
    JRadioButton button = new JRadioButton();
    button.setText(CodeInsightBundle.message("scope.option.custom"));
    return button;
  }

  @Override
  public @NotNull List<JComponent> getAdditionalComponents(JRadioButton button, ModelScopeItem m, Disposable dialogDisposable) {
    CustomScopeItem model = (CustomScopeItem) m;
    ScopeChooserCombo scopeCombo = new ScopeChooserCombo();
    Disposer.register(dialogDisposable, scopeCombo);
    scopeCombo.init(model.getProject(), model.getSearchInLibFlag(), true, model.getPreselectedCustomScope(), null);
    scopeCombo.setCurrentSelection(false);
    scopeCombo.setEnabled(button.isSelected());
    model.setSearchScopeSupplier(() -> scopeCombo.getSelectedScope());
    button.addItemListener(e -> scopeCombo.setEnabled(button.isSelected()));
    ArrayList<JComponent> components = new ArrayList<>();
    components.add(scopeCombo);
    return components;
  }

  @Override
  public boolean isApplicable(ModelScopeItem model) {
    return model instanceof CustomScopeItem;
  }

  @Override
  public @Nullable ModelScopeItem tryCreate(@NotNull Project project,
                                            @NotNull AnalysisScope scope,
                                            @Nullable Module module,
                                            @Nullable PsiElement context) {
    return new CustomScopeItem(project, context);
  }
}