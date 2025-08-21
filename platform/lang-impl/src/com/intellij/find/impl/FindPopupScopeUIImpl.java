// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.FrontendScopeChooser;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.project.module.ModulesStateService;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.dsl.gridLayout.builders.RowBuilder;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Arrays;

final class FindPopupScopeUIImpl implements FindPopupScopeUI {
  static final ScopeType PROJECT = new ScopeType(PROJECT_SCOPE_NAME, FindBundle.messagePointer("find.popup.scope.project"), EmptyIcon.ICON_0);
  static final ScopeType MODULE = new ScopeType(MODULE_SCOPE_NAME, FindBundle.messagePointer("find.popup.scope.module"), EmptyIcon.ICON_0);
  static final ScopeType DIRECTORY = new ScopeType(DIRECTORY_SCOPE_NAME, FindBundle.messagePointer("find.popup.scope.directory"), EmptyIcon.ICON_0);
  static final ScopeType SCOPE = new ScopeType(CUSTOM_SCOPE_SCOPE_NAME, FindBundle.messagePointer("find.popup.scope.scope"), EmptyIcon.ICON_0);

  private final @NotNull FindUIHelper myHelper;
  private final @NotNull Project myProject;
  private final @NotNull FindPopupPanel myFindPopupPanel;
  private final Pair<ScopeType, JComponent> @NotNull [] myComponents;

  private ComboBox<String> myModuleComboBox;
  private FindPopupDirectoryChooser myDirectoryChooser;
  private ScopeChooserCombo myScopeCombo;
  private FrontendScopeChooser newScopeCombo;

  FindPopupScopeUIImpl(@NotNull FindPopupPanel panel) {
    myHelper = panel.getHelper();
    myProject = panel.getProject();
    myFindPopupPanel = panel;
    initComponents();

    boolean fullVersion = !PlatformUtils.isDataGrip();
    myComponents =
      fullVersion
      ? ContainerUtil.ar(new Pair<>(PROJECT, new JLabel()),
                         new Pair<>(MODULE, shrink(myModuleComboBox)),
                         new Pair<>(DIRECTORY, myDirectoryChooser),
                         new Pair<>(SCOPE, shrink(getScopeChooser())))
      : ContainerUtil.ar(new Pair<>(SCOPE, shrink(getScopeChooser())),
                         new Pair<>(DIRECTORY, myDirectoryChooser));
  }

  public void initComponents() {
    String[] names = FindKey.isEnabled() ? ModulesStateService.getInstance(myProject).getModuleNames().toArray(String[]::new) :
                     Arrays.stream(ModuleManager.getInstance(myProject).getModules()).map(Module::getName).toArray(String[]::new);

    Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
    myModuleComboBox = new ComboBox<>(names);
    myModuleComboBox.setSwingPopup(false);
    myModuleComboBox.setMinimumAndPreferredWidth(JBUIScale.scale(300)); // as ScopeChooser

    myModuleComboBox.addActionListener(e -> {
      if (myFindPopupPanel.isScopeSelected(MODULE)) {
        scheduleResultsUpdate();
      }
    });

    myDirectoryChooser = new FindPopupDirectoryChooser(myFindPopupPanel);

    initScopeCombo();
  }

  private JComponent getScopeChooser() {
    return FindKey.isEnabled() ? newScopeCombo : myScopeCombo;
  }

  private ComboBox<ScopeDescriptor> getScopeCombo() {
    return FindKey.isEnabled() ? newScopeCombo.getComboBox() : myScopeCombo.getComboBox();
  }

  private void initScopeCombo() {
    ActionListener restartSearchListener = e -> {
      if (myFindPopupPanel.isScopeSelected(SCOPE)) {
        scheduleResultsUpdate();
      }
    };
    String selection = ObjectUtils.coalesce(myHelper.getModel().getCustomScopeName(), FindSettings.getInstance().getDefaultScopeName());
    if (FindKey.isEnabled()) {
      newScopeCombo = new FrontendScopeChooser(myProject, selection, ScopesFilterConditionType.FIND);
      Disposer.register(myFindPopupPanel.getDisposable(), newScopeCombo);
    }
    else {
      myScopeCombo = new ScopeChooserCombo();
      Function1<@NotNull ScopeDescriptor, @NotNull Boolean> filterByType = ScopesFilterConditionType.FIND.getScopeFilterByType();
      Condition<ScopeDescriptor> filterCondition = filterByType == null ? null : descriptor -> filterByType.invoke(descriptor);
      myScopeCombo.init(myProject, true, true, selection, filterCondition);
      myScopeCombo.setBrowseListener(new ScopeChooserCombo.BrowseListener() {

        private FindModel myModelSnapshot;

        @Override
        public void onBeforeBrowseStarted() {
          myModelSnapshot = myHelper.getModel();
          myFindPopupPanel.getCanClose().set(false);
        }

        @Override
        public void onAfterBrowseFinished() {
          if (myModelSnapshot != null) {
            SearchScope scope = myScopeCombo.getSelectedScope();
            if (scope != null) {
              myModelSnapshot.setCustomScope(scope);
            }
            myFindPopupPanel.getCanClose().set(true);
          }
        }
      });
      Disposer.register(myFindPopupPanel.getDisposable(), myScopeCombo);
    }
    getScopeChooser().getAccessibleContext().setAccessibleName(FindBundle.message("find.scope.combo.accessibleName"));
    getScopeCombo().addActionListener(restartSearchListener);
  }

  private String getSelectedScopeName() {
    if (FindKey.isEnabled()) {
      return newScopeCombo.getSelectedScopeName();
    }
    return myScopeCombo.getSelectedScopeName();
  }

  private void applyScopeTo(FindModel findModel) {
    if (FindKey.isEnabled()) {
      findModel.setCustomScopeId(newScopeCombo.getSelectedScopeId());
      findModel.setCustomScopeName(newScopeCombo.getSelectedScopeName());
    }
    else {
      SearchScope selectedCustomScope = myScopeCombo.getSelectedScope();
      String customScopeName = selectedCustomScope == null ? null : selectedCustomScope.getDisplayName();
      findModel.setCustomScopeName(customScopeName);
      findModel.setCustomScope(selectedCustomScope);
    }
  }

  @Override
  public Pair<ScopeType, JComponent> @NotNull [] getComponents() {
    return myComponents;
  }

  @Override
  public void applyTo(@NotNull FindSettings findSettings, @NotNull FindPopupScopeUI.ScopeType selectedScope) {
    findSettings.setDefaultScopeName(getSelectedScopeName());
  }

  @Override
  public void applyTo(@NotNull FindModel findModel, @NotNull FindPopupScopeUI.ScopeType selectedScope) {
    if (selectedScope == PROJECT) {
      findModel.setProjectScope(true);
    }
    else if (selectedScope == DIRECTORY) {
      String directory = myDirectoryChooser.getDirectory();
      findModel.setDirectoryName(directory);
    }
    else if (selectedScope == MODULE) {
      findModel.setModuleName((String)myModuleComboBox.getSelectedItem());
    }
    else if (selectedScope == SCOPE) {
      applyScopeTo(findModel);
      findModel.setCustomScope(true);
    }
  }

  @Override
  public @Nullable ValidationInfo validate(@NotNull FindModel model, FindPopupScopeUI.ScopeType selectedScope) {
    if (selectedScope == DIRECTORY) {
      return myDirectoryChooser.validate(model);
    }
    return null;
  }

  @Override
  public boolean hideAllPopups() {
    final JComboBox[] candidates = { myModuleComboBox, getScopeCombo(), myDirectoryChooser.getComboBox() };
    for (JComboBox candidate : candidates) {
      if (candidate.isPopupVisible()) {
        candidate.hidePopup();
        return true;
      }
    }
    return false;
  }

  @Override
  public ValidationInfo evaluateValidationInfo(Boolean isDirectoryExists) {
    return myDirectoryChooser.getDirectoryValidationInfo(isDirectoryExists);
  }

  @Override
  public boolean isDirectoryScope(FindPopupScopeUI.ScopeType selectedScope) {
    return selectedScope == DIRECTORY;
  }

  @Override
  public @NotNull ScopeType initByModel(@NotNull FindModel findModel) {
    myDirectoryChooser.initByModel(findModel);

    final String dirName = findModel.getDirectoryName();
    if (!StringUtil.isEmptyOrSpaces(dirName)) {
      VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(dirName);
      if (dir != null) {
        Module module = ModuleUtilCore.findModuleForFile(dir, myProject);
        if (module != null) {
          myModuleComboBox.setSelectedItem(module.getName());
        }
      }
    }

    ScopeType scope = getScope(findModel);
    ScopeType selectedScope = Arrays.stream(myComponents).filter(o -> o.first == scope).findFirst().orElse(null) == null
                              ? myComponents[0].first
                              : scope;
    if (selectedScope == MODULE) {
      myModuleComboBox.setSelectedItem(findModel.getModuleName());
    }
    return selectedScope;
  }

  @Override
  public ScopeType getScopeTypeByModel(@NotNull FindModel findModel) {
    return getScope(findModel);
  }

  private static JComponent shrink(JComponent toShrink) {
    JPanel wrapper = new JPanel();
    new RowBuilder(wrapper).add(toShrink);
    return wrapper;
  }

  private void scheduleResultsUpdate() {
    myFindPopupPanel.scheduleResultsUpdate();
  }

  private static ScopeType getScope(FindModel model) {
    if (model.isCustomScope()) {
      return SCOPE;
    }
    if (model.isProjectScope()) {
      return PROJECT;
    }
    if (model.getDirectoryName() != null) {
      return DIRECTORY;
    }
    if (model.getModuleName() != null) {
      return MODULE;
    }
    return PROJECT;
  }
}
