// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.application.options.ModulesCombo;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.core.JavaPsiBundle;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.SortedComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConfigurationModuleSelector {
  private final @NotNull Project myProject;
  /** this field is {@code null} if and only if {@link #myModulesList} is not null */
  private final ModulesCombo myModulesDescriptionsComboBox;
  /** this field is {@code null} if and only if {@link #myModulesDescriptionsComboBox} is not null */
  private final JComboBox<? extends Module> myModulesList;

  /**
   * @deprecated use {@link #ConfigurationModuleSelector(Project, ModulesComboBox)} instead
   */
  @Deprecated(forRemoval = true)
  public ConfigurationModuleSelector(@NotNull Project project, final JComboBox<? extends Module> modulesList) {
    String noModule = JavaPsiBundle.message("list.item.no.module");
    myProject = project;
    myModulesList = modulesList;
    myModulesDescriptionsComboBox = null;
    ComboboxSpeedSearch search = new ComboboxSpeedSearch(modulesList, null) {
      @Override
      protected String getElementText(Object element) {
        if (element instanceof Module) {
          return ((Module)element).getName();
        }
        else if (element == null) {
          return noModule;
        }
        return super.getElementText(element);
      }
    };
    search.setupListeners();
    myModulesList.setModel(new SortedComboBoxModel<>(ModulesAlphaComparator.INSTANCE));
    myModulesList.setRenderer(new ModuleListCellRenderer(noModule));
  }

  public ConfigurationModuleSelector(@NotNull Project project, ModulesComboBox modulesComboBox) {
    this(project, modulesComboBox, JavaPsiBundle.message("list.item.no.module"));
  }

  public ConfigurationModuleSelector(@NotNull Project project, ModuleDescriptionsComboBox modulesDescriptionsComboBox) {
    this(project, modulesDescriptionsComboBox, JavaPsiBundle.message("list.item.no.module"));
  }

  public ConfigurationModuleSelector(@NotNull Project project, ModulesCombo modulesDescriptionsComboBox) {
    this(project, modulesDescriptionsComboBox, JavaPsiBundle.message("list.item.no.module"));
  }

  private ConfigurationModuleSelector(@NotNull Project project, ModulesCombo modulesDescriptionsComboBox, @NlsContexts.ListItem @Nullable String emptySelectionText) {
    myProject = project;
    myModulesDescriptionsComboBox = modulesDescriptionsComboBox;
    myModulesList = null;
    if (emptySelectionText != null) {
      modulesDescriptionsComboBox.allowEmptySelection(emptySelectionText);
    }
  }

  public ConfigurationModuleSelector(@NotNull Project project, ModulesComboBox modulesComboBox, @NlsContexts.ListItem String noModule) {
    myProject = project;
    myModulesList = modulesComboBox;
    myModulesDescriptionsComboBox = null;
    modulesComboBox.allowEmptySelection(noModule);
  }

  public void applyTo(final ModuleBasedConfiguration configurationModule) {
    if (myModulesList != null) {
      configurationModule.setModule((Module)myModulesList.getSelectedItem());
    }
    else {
      configurationModule.setModuleName(myModulesDescriptionsComboBox.getSelectedModuleName());
    }
  }

  public void reset(final ModuleBasedConfiguration configuration) {
    reset();
    if (myModulesList != null) {
      myModulesList.setSelectedItem(configuration.getConfigurationModule().getModule());
    }
    else {
      myModulesDescriptionsComboBox.setSelectedModule(myProject, configuration.getConfigurationModule().getModuleName());
    }
  }

  public void reset() {
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    final List<Module> list = new ArrayList<>();
    for (final Module module : modules) {
      if (isModuleAccepted(module)) {
        list.add(module);
      }
    }
    setModules(list);
  }

  public boolean isModuleAccepted(final Module module) {
    return ModuleTypeManager.getInstance().isClasspathProvider(ModuleType.get(module));
  }

  public Project getProject() {
    return myProject;
  }

  public JavaRunConfigurationModule getConfigurationModule() {
    final JavaRunConfigurationModule configurationModule = new JavaRunConfigurationModule(getProject(), false);
    configurationModule.setModule(getModule());
    return configurationModule;
  }

  private void setModules(final Collection<? extends Module> modules) {
    if (myModulesDescriptionsComboBox != null) {
      myModulesDescriptionsComboBox.setModules(modules);
    }
    else if (myModulesList instanceof ModulesComboBox) {
      ((ModulesComboBox)myModulesList).setModules(modules);
    }
    else {
      SortedComboBoxModel<Module> model = (SortedComboBoxModel<Module>)myModulesList.getModel();
      model.setAll(modules);
      model.add(null);
    }
  }

  public Module getModule() {
    return myModulesDescriptionsComboBox != null ? myModulesDescriptionsComboBox.getSelectedModule() : (Module) myModulesList.getSelectedItem();
  }

  public @Nullable PsiClass findClass(final String className) {
    return getConfigurationModule().findClass(className);
  }

  public String getModuleName() {
    final Module module = getModule();
    return module == null ? "" : module.getName();
  }
}
