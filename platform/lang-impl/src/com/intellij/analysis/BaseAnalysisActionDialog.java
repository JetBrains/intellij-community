/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.analysis;

import com.intellij.analysis.dialog.*;
import com.intellij.find.FindSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class BaseAnalysisActionDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance(BaseAnalysisActionDialog.class);

  @NotNull private final AnalysisUIOptions myOptions;
  private final boolean myRememberScope;
  private final boolean myShowInspectTestSource;
  private final String myAnalysisNoon;
  private final Project myProject;
  private final ButtonGroup myGroup = new ButtonGroup();
  private final JBCheckBox myInspectTestSource = new JBCheckBox();
  private final List<ModelScopeItemView> myViewItems;

  // backward compatibility
  @Deprecated
  public BaseAnalysisActionDialog(@NotNull String title,
                                   @NotNull String analysisNoon,
                                   @NotNull Project project,
                                   @NotNull final AnalysisScope scope,
                                   final String moduleName,
                                   final boolean rememberScope,
                                   @NotNull AnalysisUIOptions analysisUIOptions,
                                   @Nullable PsiElement context) {
    this(title, analysisNoon, project, scope, moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null, rememberScope, analysisUIOptions, context);
  }

  // backward compatibility
  @Deprecated
  public BaseAnalysisActionDialog(@NotNull String title,
                                  @NotNull String analysisNoon,
                                  @NotNull Project project,
                                  @NotNull final AnalysisScope scope,
                                  @Nullable Module module,
                                  final boolean rememberScope,
                                  @NotNull AnalysisUIOptions analysisUIOptions,
                                  @Nullable PsiElement context) {
    this(title, analysisNoon, project, Stream.of(new ProjectScopeItem(project),
                                                 new CustomScopeItem(project, context),
                                                 VcsScopeItem.createIfHasVCS(project),
                                                 ModuleScopeItem.tryCreate(module),
                                                 OtherScopeItem.tryCreate(scope)).filter(x -> x != null).collect(Collectors.toList()),
         analysisUIOptions, rememberScope, ModuleUtil.isSupportedRootType(project, JavaSourceRootType.TEST_SOURCE));
  }

  public BaseAnalysisActionDialog(@NotNull String title,
                                   @NotNull String analysisNoon,
                                   @NotNull Project project,
                                   @NotNull List<ModelScopeItem> items,
                                   @NotNull AnalysisUIOptions options,
                                   final boolean rememberScope,
                                   final boolean showInspectTestSource) {
    super(true);
    myAnalysisNoon = analysisNoon;
    myProject = project;

    myViewItems = ModelScopeItemPresenter.createOrderedViews(items);
    myOptions = options;
    myRememberScope = rememberScope;
    myShowInspectTestSource = showInspectTestSource;

    init();
    setTitle(title);
  }

  @Override
  protected JComponent createCenterPanel() {
    BorderLayoutPanel panel = new BorderLayoutPanel();
    TitledSeparator titledSeparator = new TitledSeparator();
    titledSeparator.setText(myAnalysisNoon);
    panel.addToTop(titledSeparator);

    JPanel scopesPanel = new JPanel(new GridBagLayout());
    panel.addToCenter(scopesPanel);

    int maxColumns = myViewItems.stream()
                       .mapToInt(x -> x.additionalComponents.size())
                       .max().orElse(0) + 1;

    int gridY = 0;
    JRadioButton[] buttons = new JRadioButton[myViewItems.size()];
    for (ModelScopeItemView x: myViewItems) {
      JRadioButton button = x.button;
      List<JComponent> components = x.additionalComponents;

      int gridX = 0;
      buttons[gridY] = button;
      myGroup.add(button);
      int countExtraColumns = components.size();
      if (countExtraColumns == 0) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = gridY;
        constraints.gridx = gridX;
        constraints.gridwidth = maxColumns;
        constraints.anchor = GridBagConstraints.WEST;
        scopesPanel.add(button, constraints);
        gridX++;
      }
      else {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = gridY;
        constraints.gridx = gridX;
        constraints.gridwidth = 1;
        constraints.anchor = GridBagConstraints.WEST;
        scopesPanel.add(button, constraints);
        gridX++;
      }

      for (JComponent c : components) {
        if (c instanceof Disposable) {
          Disposer.register(myDisposable, (Disposable)c);
        }
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = gridY;
        constraints.gridx = gridX;
        constraints.gridwidth = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.EAST;
        scopesPanel.add(c, constraints);
        gridX++;
      }
      gridY++;
    }

    myInspectTestSource.setText(AnalysisScopeBundle.message("scope.option.include.test.sources"));
    myInspectTestSource.setSelected(myOptions.ANALYZE_TEST_SOURCES);
    myInspectTestSource.setVisible(myShowInspectTestSource);
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridy = gridY;
    constraints.gridx = 0;
    constraints.gridwidth = maxColumns;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.WEST;
    scopesPanel.add(myInspectTestSource, constraints);

    preselectButton();

    BorderLayoutPanel wholePanel = new BorderLayoutPanel();
    wholePanel.addToTop(panel);
    final JComponent additionalPanel = getAdditionalActionSettings(myProject);
    if (additionalPanel != null) {
      wholePanel.addToBottom(additionalPanel);
    }
    new RadioUpDownListener(buttons);

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> getWindow().pack());
    return wholePanel;
  }

  private void preselectButton() {
    if (myRememberScope) {
      int type = myOptions.SCOPE_TYPE;
      List<ModelScopeItemView> preselectedScopes = myViewItems.stream()
        .filter(x -> x.scopeId == type).collect(Collectors.toList());

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
    final Enumeration<AbstractButton> enumeration = myGroup.getElements();
    while (enumeration.hasMoreElements()) {
      final AbstractButton button = enumeration.nextElement();
      if (button.isSelected()) {
        return button;
      }
    }
    return super.getPreferredFocusedComponent();
  }

  @Deprecated
  public AnalysisScope getScope(@NotNull AnalysisUIOptions uiOptions, @NotNull AnalysisScope defaultScope, @NotNull Project project, Module module) {
    return getScope(defaultScope);
  }

  public boolean isInspectTestSources() {
    return myInspectTestSource.isSelected();
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

    FindSettings.getInstance().setDefaultScopeName(scope.getDisplayName());
    return scope;
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project) {
    return null;
  }
}
