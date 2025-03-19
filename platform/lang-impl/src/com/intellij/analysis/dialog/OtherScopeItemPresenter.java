// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.dialog;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class OtherScopeItemPresenter implements ModelScopeItemPresenter {

  @Override
  public int getScopeId() {
    return AnalysisScope.FILE;
  }

  @Override
  public @NotNull JRadioButton getButton(ModelScopeItem m) {
    OtherScopeItem model = (OtherScopeItem)m;
    AnalysisScope scope = model.getScope();
    JRadioButton button = new JRadioButton();
    String name = scope.getShortenName();
    button.setText(name);
    button.setMnemonic(name.charAt(getSelectedScopeMnemonic(name)));
    return button;
  }

  @Override
  public @NotNull List<JComponent> getAdditionalComponents(JRadioButton b, ModelScopeItem m, Disposable dialogDisposable) {
    return Collections.emptyList();
  }

  @Override
  public boolean isApplicable(ModelScopeItem model) {
    return model instanceof OtherScopeItem;
  }

  private static int getSelectedScopeMnemonic(String name) {

    final int fileIdx = StringUtil.indexOfIgnoreCase(name, "file", 0);
    if (fileIdx > -1) {
      return fileIdx;
    }

    final int dirIdx = StringUtil.indexOfIgnoreCase(name, "directory", 0);
    if (dirIdx > -1) {
      return dirIdx;
    }

    return 0;
  }

  @Override
  public @Nullable ModelScopeItem tryCreate(@NotNull Project project,
                                            @NotNull AnalysisScope scope,
                                            @Nullable Module module,
                                            @Nullable PsiElement context) {
    return OtherScopeItem.tryCreate(scope);
  }
}