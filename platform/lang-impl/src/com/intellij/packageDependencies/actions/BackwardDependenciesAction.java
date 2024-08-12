// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class BackwardDependenciesAction extends BaseAnalysisAction {
  private BackwardDependenciesAdditionalUi myPanel;


  public BackwardDependenciesAction() {
    super(CodeInsightBundle.messagePointer("action.backward.dependency.analysis"), CodeInsightBundle.messagePointer("action.analysis.noun"));
  }

  @Override
  protected void analyze(final @NotNull Project project, final @NotNull AnalysisScope scope) {
    scope.setSearchInLibraries(true); //find library usages in project
    final SearchScope selectedScope = myPanel.getScopeChooserCombo().getSelectedScope();
    new BackwardDependenciesHandler(project, scope, selectedScope != null ? new AnalysisScope(selectedScope, project) : new AnalysisScope(project)).analyze();
    dispose();
  }

  @Override
  protected boolean acceptNonProjectDirectories() {
    return true;
  }

  @Override
  protected void canceled() {
    super.canceled();
    dispose();
  }

  private void dispose() {
    Disposer.dispose(myPanel.getScopeChooserCombo());
    myPanel = null;
  }

  @Override
  protected @Nullable JComponent getAdditionalActionSettings(final @NotNull Project project, final BaseAnalysisActionDialog dialog) {
    myPanel = new BackwardDependenciesAdditionalUi();
    myPanel.getScopeChooserCombo().init(project, null);
    return myPanel.getPanel();
  }

}
