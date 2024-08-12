// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.ConfigurableFactory;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.application.options.codeStyle.group.CodeStyleGroupProvider;
import com.intellij.application.options.codeStyle.group.CodeStyleGroupProviderFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.codeStyle.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public final class CodeStyleSchemesConfigurable extends SearchableConfigurable.Parent.Abstract
  implements Configurable.NoMargin, Configurable.NoScroll, Configurable.VariableProjectAppLevel, Configurable.WithEpDependencies {

  public static final String CONFIGURABLE_ID = "preferences.sourceCode";

  private final @NotNull CodeStyleSchemesModel myModel;
  private List<Configurable> myPanels;
  private boolean myResetCompleted = false;
  private boolean myInitResetInvoked = false;
  private boolean myRevertCompleted = false;

  private final Project myProject;

  public CodeStyleSchemesConfigurable(Project project) {
    myProject = project;
    myModel = new CodeStyleSchemesModel(project);
  }

  @Override
  public JComponent createComponent() {
    return myPanels == null || myPanels.isEmpty() ? null : myPanels.get(0).createComponent();
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public void disposeUIResources() {
    if (myPanels != null) {
      try {
        super.disposeUIResources();
        for (Configurable panel : myPanels) {
          panel.disposeUIResources();
        }
      }
      finally {
        myPanels = null;
        myResetCompleted = false;
        myRevertCompleted = false;
        myInitResetInvoked = false;
      }
    }
  }

  @Override
  public synchronized void reset() {
    if (!myInitResetInvoked) {
      try {
        if (!myResetCompleted) {
          try {
            resetImpl();
          }
          finally {
            myResetCompleted = true;
          }
        }
      }
      finally {
        myInitResetInvoked = true;
      }
    }
    else {
      revert();
    }
  }

  private void resetImpl() {
    myModel.reset();

    if (myPanels != null) {
      for (Configurable panel : myPanels) {
        if (panel instanceof CodeStyleConfigurableWrapper) {
          ((CodeStyleConfigurableWrapper)panel).resetPanel();
        }
        else {
          panel.reset();
        }
      }
    }
  }

  public synchronized void resetFromChild() {
    if (!myResetCompleted) {
      try {
        resetImpl();
      }
      finally {
        myResetCompleted = true;
      }
    }
  }

  public void revert() {
    if (myModel.isSchemeListModified() || isSomeSchemeModified()) {
      myRevertCompleted = false;
    }
    if (!myRevertCompleted) {
      try {
        resetImpl();
      }
      finally {
        myRevertCompleted = true;
      }
    }
  }

  private boolean isSomeSchemeModified() {
    if (myPanels != null) {
      for (Configurable panel : myPanels) {
        if (panel instanceof CodeStyleConfigurableWrapper) {
          if (((CodeStyleConfigurableWrapper)panel).isPanelModified()) return true;
        }
      }
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    myModel.apply();

    if (myPanels != null) {
      for (Configurable panel : myPanels) {
        if (panel instanceof CodeStyleConfigurableWrapper) {
          ((CodeStyleConfigurableWrapper)panel).applyPanel();
        }
        else {
          panel.apply();
        }
      }
    }

    SimpleModificationTracker codeStyleModificationTracker = CodeStyle.getSettings(myProject).getModificationTracker();
    long settingsModificationCount = codeStyleModificationTracker.getModificationCount();
    CodeStyleSettingsManager.getInstance(myProject).fireCodeStyleSettingsChanged();
    if (settingsModificationCount != codeStyleModificationTracker.getModificationCount()) {
      myModel.updateClonedSettings();
    }
  }

  @Override
  protected Configurable[] buildConfigurables() {
    CodeStyleGroupProviderFactory groupProviderFactory = new CodeStyleGroupProviderFactory(getModel(), this);
    myPanels = new ArrayList<>();

    Comparator<CodeStyleSettingsProvider> providerComparator =
      (p1, p2) -> DisplayPrioritySortable.compare(p1, p2, p -> p.getConfigurableDisplayName());

    final List<CodeStyleSettingsProvider> providers = new ArrayList<>();
    providers.addAll(CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList());
    providers.addAll(LanguageCodeStyleSettingsProvider.getSettingsPagesProviders());

    // sort so that CodeStyleGroupProvider get their children sorted
    providers.sort(providerComparator);

    List<CodeStyleSettingsProvider> settingsProviders = new ArrayList<>();
    Set<CodeStyleGroupProvider> addedGroupProviders = new HashSet<>();
    for (final CodeStyleSettingsProvider provider : providers) {
      CodeStyleGroup group = provider.getGroup();
      if (group != null) {
        CodeStyleGroupProvider groupProvider = groupProviderFactory.getGroupProvider(group);
        groupProvider.addChildProvider(provider);

        if (addedGroupProviders.add(groupProvider)) {
          settingsProviders.add(groupProvider);
        }
      }
      else if (provider.hasSettingsPage()) {
        settingsProviders.add(provider);
      }
    }

    // sort again: replacement CodeStyleGroupProvider might have a different name and order
    settingsProviders.sort(providerComparator);

    for (CodeStyleSettingsProvider provider : settingsProviders) {
      if (provider instanceof CodeStyleGroupProvider groupProvider) {
        myPanels.add(groupProvider.createConfigurable());
      }
      else {
        myPanels.add(ConfigurableFactory.getInstance().createCodeStyleConfigurable(provider, getModel(), this));
      }
    }

    int size = myPanels.size();
    Configurable[] result = new Configurable[size > 0 ? size - 1 : 0];
    for (int i = 0; i < result.length; i++) {
      result[i] = myPanels.get(i + 1);
    }
    return result;
  }

  void resetCompleted() {
    myRevertCompleted = false;
  }

  @NotNull
  CodeStyleSchemesModel getModel() {
    return myModel;
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("configurable.CodeStyleSchemesConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle";
  }

  @Override
  public boolean isModified() {
    if (myModel.containsModifiedCodeStyleSettings()) return true;
    if (myPanels != null) {
      for (Configurable panel : myPanels) {
        if (panel.isModified()) return true;
      }
    }
    boolean schemeListModified = myModel.isSchemeListModified();
    if (schemeListModified) {
      myRevertCompleted = false;
    }
    return schemeListModified;
  }

  @Override
  public @NotNull String getId() {
    return CONFIGURABLE_ID;
  }

  @Override
  public boolean isProjectLevel() {
    return myModel.isUsePerProjectSettings();
  }

  public @Nullable SearchableConfigurable findSubConfigurable(final @NotNull String name) {
    return findSubConfigurable(this, name);
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Arrays.asList(new ExtensionPointName<?>[]{
      LanguageCodeStyleSettingsProvider.EP_NAME,
      CodeStyleSettingsProvider.EXTENSION_POINT_NAME
    });
  }

  private static SearchableConfigurable findSubConfigurable(SearchableConfigurable.Parent topConfigurable, final @NotNull String name) {
    for (Configurable configurable : topConfigurable.getConfigurables()) {
      if (configurable instanceof SearchableConfigurable) {
        if (name.equals(configurable.getDisplayName())) return (SearchableConfigurable)configurable;
        if (configurable instanceof SearchableConfigurable.Parent) {
          SearchableConfigurable child = findSubConfigurable((Parent)configurable, name);
          if (child != null) return child;
        }
      }
    }
    return null;
  }
}
