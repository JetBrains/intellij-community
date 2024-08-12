// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis;

import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.analysis.dialog.ModelScopeItemPresenter;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.find.FindSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class BaseAnalysisActionDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(BaseAnalysisActionDialog.class);

  private final @NotNull AnalysisUIOptions myOptions;
  private final boolean myRememberScope;
  private final boolean myShowInspectTestSource;
  private final @NlsContexts.Separator String myScopeTitle;
  private final @NotNull Project myProject;
  private final ArrayList<JRadioButton> radioButtons = new ArrayList<>();
  private final JCheckBox myInspectTestSource = new JCheckBox();
  private final JCheckBox myAnalyzeInjectedCode = new JCheckBox();
  private final List<ModelScopeItemView> myViewItems;

  /**
   * @deprecated Use {@link BaseAnalysisActionDialog#BaseAnalysisActionDialog(String, String, Project, List, AnalysisUIOptions, boolean, boolean)} instead.
   */
  @Deprecated
  public BaseAnalysisActionDialog(@NlsContexts.DialogTitle @NotNull String title,
                                  @NotNull @NlsContexts.Separator String scopeTitle,
                                  @NotNull Project project,
                                  final @NotNull AnalysisScope scope,
                                  final String moduleName,
                                  final boolean rememberScope,
                                  @NotNull AnalysisUIOptions analysisUIOptions,
                                  @Nullable PsiElement context) {
    this(title, scopeTitle, project, standardItems(project, scope, moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null, context),
         analysisUIOptions, rememberScope);
  }

  protected @Nullable JComponent getAdditionalActionSettings(@NotNull Project project) {
    return null;
  }

  public BaseAnalysisActionDialog(@NlsContexts.DialogTitle @NotNull String title,
                                @NotNull @NlsContexts.Separator String scopeTitle,
                                @NotNull Project project,
                                @NotNull List<? extends ModelScopeItem> items,
                                @NotNull AnalysisUIOptions options,
                                final boolean rememberScope) {
    this(title, scopeTitle, project, items, options, rememberScope, ModuleUtil.hasTestSourceRoots(project));
  }

  public BaseAnalysisActionDialog(@NlsContexts.DialogTitle @NotNull String title,
                                  @NotNull @NlsContexts.Separator String scopeTitle,
                                  @NotNull Project project,
                                  @NotNull List<? extends ModelScopeItem> items,
                                  @NotNull AnalysisUIOptions options,
                                  final boolean rememberScope,
                                  final boolean showInspectTestSource) {
    super(true);
    myScopeTitle = scopeTitle;
    myProject = project;

    myViewItems = ModelScopeItemPresenter.createOrderedViews(items, getDisposable());
    myOptions = options;
    myRememberScope = rememberScope;
    myShowInspectTestSource = showInspectTestSource;

    init();
    setTitle(title);
    setResizable(false);
    setOKButtonText(getOKButtonText());
  }

  @Override
  protected JComponent createCenterPanel() {
    myInspectTestSource.setText(CodeInsightBundle.message("scope.option.include.test.sources"));
    myInspectTestSource.setSelected(myOptions.ANALYZE_TEST_SOURCES);
    myInspectTestSource.setVisible(myShowInspectTestSource);
    myAnalyzeInjectedCode.setText(CodeInsightBundle.message("scope.option.analyze.injected.code"));
    myAnalyzeInjectedCode.setSelected(myOptions.ANALYZE_INJECTED_CODE);
    myAnalyzeInjectedCode.setVisible(false);

    JPanel panel = new BaseAnalysisActionDialogUI().panel(myScopeTitle, myViewItems, myInspectTestSource,
                                                          myAnalyzeInjectedCode, radioButtons, myDisposable,
                                                          getAdditionalActionSettings(myProject));

    preselectButton();
    RadioUpDownListener.installOn(radioButtons.toArray(new JRadioButton[0]));

    panel.setPreferredSize(panel.getMinimumSize());
    return panel;
  }

  public void setShowInspectInjectedCode(boolean showInspectInjectedCode) {
    myAnalyzeInjectedCode.setVisible(showInspectInjectedCode);
  }
  
  public void setAnalyzeInjectedCode(boolean selected) {
    myAnalyzeInjectedCode.setSelected(selected);
  }

  private void preselectButton() {
    if (myRememberScope) {
      int type = myOptions.SCOPE_TYPE;
      List<ModelScopeItemView> preselectedScopes = ContainerUtil.filter(myViewItems, x -> x.scopeId == type);

      if (preselectedScopes.size() >= 1) {
        LOG.assertTrue(preselectedScopes.size() == 1, "preselectedScopes.size() == 1");
        preselectedScopes.get(0).button.setSelected(true);
        return;
      }
    }

    List<ModelScopeItemView> candidates = new ArrayList<>();
    for (ModelScopeItemView view : myViewItems) {
      candidates.add(view);
      if (view.scopeId == AnalysisScope.FILE) {
        break;
      }
    }

    Collections.reverse(candidates);
    for (ModelScopeItemView x : candidates) {
      int scopeType = x.scopeId;
      // skip predefined scopes
      if (scopeType == AnalysisScope.CUSTOM || scopeType == AnalysisScope.UNCOMMITTED_FILES) {
        continue;
      }
      x.button.setSelected(true);
      break;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    for (JRadioButton button : radioButtons) {
      if (button.isSelected()) {
        return button;
      }
    }
    return super.getPreferredFocusedComponent();
  }

  /**
   * @deprecated Use {@link BaseAnalysisActionDialog#getScope(AnalysisScope)} instead.
   */
  @Deprecated
  public AnalysisScope getScope(@NotNull AnalysisUIOptions uiOptions, @NotNull AnalysisScope defaultScope, @NotNull Project project, Module module) {
    return getScope(defaultScope);
  }

  public boolean isProjectScopeSelected() {
    return myViewItems.stream()
      .filter(x -> x.scopeId == AnalysisScope.PROJECT)
      .findFirst().map(x -> x.button.isSelected()).orElse(false);
  }

  public boolean isInspectTestSources() {
    return myInspectTestSource.isSelected();
  }

  public boolean isAnalyzeInjectedCode() {
    return !myAnalyzeInjectedCode.isVisible() || myAnalyzeInjectedCode.isSelected();
  }

  public AnalysisScope getScope(@NotNull AnalysisScope defaultScope) {
    AnalysisScope scope = null;
    for (ModelScopeItemView x : myViewItems) {
      if (x.button.isSelected()) {
        int type = x.scopeId;
        scope = x.model.getScope();
        if (myRememberScope) {
          myOptions.SCOPE_TYPE = type;
          if (type == AnalysisScope.CUSTOM) {
            myOptions.CUSTOM_SCOPE_NAME = scope.toSearchScope().getDisplayName();
          }
        }
      }
    }
    if (scope == null) {
      scope = defaultScope;
      if (myRememberScope) {
        myOptions.SCOPE_TYPE = scope.getScopeType();
      }
    }

    if (myInspectTestSource.isVisible()) {
      if (myRememberScope) {
        myOptions.ANALYZE_TEST_SOURCES = isInspectTestSources();
      }
      scope.setIncludeTestSource(isInspectTestSources());
    }

    if (myAnalyzeInjectedCode.isVisible()) {
      boolean analyzeInjectedCode = isAnalyzeInjectedCode();
      if (myRememberScope) {
        myOptions.ANALYZE_INJECTED_CODE = analyzeInjectedCode;
      }
      scope.setAnalyzeInjectedCode(analyzeInjectedCode);
    }

    FindSettings.getInstance().setDefaultScopeName(scope.getDisplayName());
    return scope;
  }

  public @NotNull @Nls String getOKButtonText() {
    return CodeInsightBundle.message("action.analyze.verb");
  }

  public static @NotNull List<ModelScopeItem> standardItems(@NotNull Project project,
                                                            @NotNull AnalysisScope scope,
                                                            @Nullable Module module,
                                                            @Nullable PsiElement context) {
    return ContainerUtil.mapNotNull(
      ModelScopeItemPresenter.EP_NAME.getExtensionList(),
      presenter -> presenter.tryCreate(project, scope, module, context));
  }
}
