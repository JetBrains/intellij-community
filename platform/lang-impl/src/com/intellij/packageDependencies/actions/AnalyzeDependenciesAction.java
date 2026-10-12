// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.SpinnerNumberModel;

// todo must be moved to Java plugin
final class AnalyzeDependenciesAction extends BaseAnalysisAction {
  private AnalyzeDependenciesAdditionalUi myPanel;

  AnalyzeDependenciesAction() {
    super(CodeInsightBundle.messagePointer("action.forward.dependency.analysis"), CodeInsightBundle.messagePointer("action.analysis.noun"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    if (ActionPlaces.ACTION_SEARCH.equals(e.getPlace())) {
      e.getPresentation().setText(ActionsBundle.message("action.ShowPackageDeps.text.full"));
    }
  }

  @Override
  protected void analyze(final @NotNull Project project, @NotNull AnalysisScope scope) {
    new AnalyzeDependenciesHandler(project, scope, myPanel.getTransitiveCB().isSelected() ? ((SpinnerNumberModel)myPanel.getBorderChooser().getModel()).getNumber().intValue() : 0).analyze();
    myPanel = null;
  }

  @Override
  protected @NotNull JComponent getAdditionalActionSettings(final @NotNull Project project, final BaseAnalysisActionDialog dialog) {
    myPanel = new AnalyzeDependenciesAdditionalUi();
    return myPanel.getPanel();
  }

  @Override
  protected void canceled() {
    super.canceled();
    myPanel = null;
  }

}
