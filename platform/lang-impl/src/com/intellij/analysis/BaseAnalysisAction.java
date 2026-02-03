// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis;

import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

public abstract class BaseAnalysisAction extends AnAction {
  private final Supplier<@DialogTitle String> myTitle;
  private final Supplier<String> myAnalysisNoun;

  protected BaseAnalysisAction(@DialogTitle String title,
                               @Nls(capitalization = Nls.Capitalization.Title) String analysisNoun) {
    myTitle = () -> title;
    myAnalysisNoun = () -> analysisNoun;
  }

  protected BaseAnalysisAction(Supplier<String> title, Supplier<String> analysisNoun) {
    myTitle = title;
    myAnalysisNoun = analysisNoun;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !DumbService.isDumb(project) && getInspectionScope(e.getDataContext(), project) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    DataContext dataContext = e.getDataContext();
    AnalysisScope scope = getInspectionScope(dataContext, project);
    if (scope == null) return;

    String title = getDialogTitle();
    String scopeTitle = CodeInsightBundle.message("analysis.scope.title", myAnalysisNoun.get());
    Module module = getModuleFromContext(dataContext);
    boolean rememberScope = ActionPlaces.isMainMenuOrActionSearch(e.getPlace());
    AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    List<ModelScopeItem> items = BaseAnalysisActionDialog.standardItems(project, scope, module, element);
    BaseAnalysisActionDialog dlg = getAnalysisDialog(project, title, scopeTitle, rememberScope, uiOptions, items);
    if (!dlg.showAndGet()) {
      canceled();
      return;
    }

    int oldScopeType = uiOptions.SCOPE_TYPE;
    scope = dlg.getScope(scope);
    if (!rememberScope) {
      uiOptions.SCOPE_TYPE = oldScopeType;
    }
    uiOptions.ANALYZE_TEST_SOURCES = dlg.isInspectTestSources();

    FileDocumentManager.getInstance().saveAllDocuments();
    analyze(project, scope);
  }

  public @NotNull BaseAnalysisActionDialog getAnalysisDialog(Project project,
                                                             @DialogTitle String title,
                                                             @NlsSafe String scopeTitle,
                                                             boolean rememberScope,
                                                             AnalysisUIOptions uiOptions,
                                                             List<? extends ModelScopeItem> items) {
    return new BaseAnalysisActionDialog(title, scopeTitle, project, items, uiOptions, rememberScope) {
      @Override
      protected JComponent getAdditionalActionSettings(@NotNull Project project) {
        return BaseAnalysisAction.this.getAdditionalActionSettings(project, this);
      }

      @Override
      protected String getHelpId() {
        return getHelpTopic();
      }
    };
  }

  protected @NotNull @DialogTitle String getDialogTitle() {
    return CodeInsightBundle.message("specify.analysis.scope", myTitle.get());
  }

  protected String getHelpTopic() {
    return "reference.dialogs.analyzeDependencies.scope";
  }

  protected void canceled() { }

  protected abstract void analyze(@NotNull Project project, @NotNull AnalysisScope scope);

  private @Nullable AnalysisScope getInspectionScope(@NotNull DataContext dataContext, @NotNull Project project) {
    return AnalysisActionUtils.getInspectionScope(dataContext, project, acceptNonProjectDirectories());
  }

  protected boolean acceptNonProjectDirectories() {
    return false;
  }

  protected @Nullable JComponent getAdditionalActionSettings(@NotNull Project project, BaseAnalysisActionDialog dialog) {
    return null;
  }

  private static @Nullable Module getModuleFromContext(@NotNull DataContext dataContext) {
    InspectionResultsView inspectionView = dataContext.getData(InspectionResultsView.DATA_KEY);
    if (inspectionView != null) {
      AnalysisScope scope = inspectionView.getScope();
      if (scope.getScopeType() == AnalysisScope.MODULE && scope.isValid()) {
        return scope.getModule();
      }
    }
    return dataContext.getData(PlatformCoreDataKeys.MODULE);
  }
}