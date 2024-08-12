// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class AnalyzeDependenciesAction extends BaseAnalysisAction {
  private AnalyzeDependenciesAdditionalUi myPanel;

  public AnalyzeDependenciesAction() {
    super(CodeInsightBundle.messagePointer("action.forward.dependency.analysis"), CodeInsightBundle.messagePointer("action.analysis.noun"));
  }

  @Override
  protected void analyze(final @NotNull Project project, @NotNull AnalysisScope scope) {
    new AnalyzeDependenciesHandler(project, scope, myPanel.getTransitiveCB().isSelected() ? ((SpinnerNumberModel)myPanel.getBorderChooser().getModel()).getNumber().intValue() : 0).analyze();
    myPanel = null;
  }

  @Override
  protected @Nullable JComponent getAdditionalActionSettings(final @NotNull Project project, final BaseAnalysisActionDialog dialog) {
    myPanel = new AnalyzeDependenciesAdditionalUi();
    return myPanel.getPanel();
  }

  @Override
  protected void canceled() {
    super.canceled();
    myPanel = null;
  }

}
