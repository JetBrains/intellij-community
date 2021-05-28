// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class BackwardDependenciesAction extends BaseAnalysisAction {
  private AdditionalSettingsPanelUi myPanel;


  public BackwardDependenciesAction() {
    super(CodeInsightBundle.messagePointer("action.backward.dependency.analysis"), CodeInsightBundle.messagePointer("action.analysis.noun"));
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
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
  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    myPanel = new AdditionalSettingsPanelUi();
    myPanel.getScopeChooserCombo().init(project, null);
    return myPanel.getPanel();
  }

}
