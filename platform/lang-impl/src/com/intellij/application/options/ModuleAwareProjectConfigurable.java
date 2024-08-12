// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.platform.ModuleAttachProcessor;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class ModuleAwareProjectConfigurable<T extends UnnamedConfigurable> implements SearchableConfigurable,
                                                                                               Configurable.NoScroll {
  private static final String PROJECT_ITEM_KEY = "thisisnotthemoduleyouarelookingfor";
  private final @NlsContexts.ConfigurableName String myDisplayName;
  private final String myHelpTopic;
  private final Map<Module, AtomicNotNullLazyValue<T>> myConfigurablesProviders = new HashMap<>();
  private final @NotNull Project myProject;

  public ModuleAwareProjectConfigurable(@NotNull Project project, @NlsContexts.ConfigurableName String displayName, @NonNls String helpTopic) {
    myProject = project;
    myDisplayName = displayName;
    myHelpTopic = helpTopic;
  }

  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public String getHelpTopic() {
    return myHelpTopic;
  }

  protected boolean isSuitableForModule(@NotNull Module module) {
    return true;
  }

  @Override
  public JComponent createComponent() {
    if (myProject.isDefault()) {
      T configurable = createDefaultProjectConfigurable();
      if (configurable != null) {
        var projectConfigurableProvider = AtomicNotNullLazyValue.createValue(() -> configurable);
        myConfigurablesProviders.put(null, projectConfigurableProvider);
        return projectConfigurableProvider.getValue().createComponent();
      }
    }
    final List<Module> modules = ContainerUtil.filter(ModuleAttachProcessor.getSortedModules(myProject),
                                                      module -> isSuitableForModule(module));

    final T projectConfigurable = createProjectConfigurable();

    if (modules.size() == 1 && projectConfigurable == null) {
      Module module = modules.get(0);
      var onlyModuleConfigurableProvider = AtomicNotNullLazyValue.createValue(() -> createModuleConfigurable(module));
      myConfigurablesProviders.put(module, onlyModuleConfigurableProvider);
      return onlyModuleConfigurableProvider.getValue().createComponent();
    }
    final Splitter splitter = new Splitter(false, 0.25f);
    CollectionListModel<Module> listDataModel = new CollectionListModel<>(modules);
    final JBList<Module> moduleList = new JBList<>(listDataModel);
    ListSpeedSearch.installOn(moduleList, o -> o == null ? getProjectConfigurableItemName() : o.getName());
    moduleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    moduleList.setCellRenderer(new ModuleListCellRenderer() {
      @Override
      public void customize(@NotNull JList<? extends Module> list, Module module, int index, boolean selected, boolean hasFocus) {
        if (module == null) {
          setText(getProjectConfigurableItemName());
          setIcon(getProjectConfigurableItemIcon());
        }
        else {
          super.customize(list, module, index, selected, hasFocus);
        }
      }
    });
    splitter.setFirstComponent(new JBScrollPane(moduleList));
    final CardLayout layout = new CardLayout();
    final JPanel cardPanel = new JPanel(layout);
    splitter.setSecondComponent(cardPanel);


    if (projectConfigurable != null) {
      myConfigurablesProviders.put(null, AtomicNotNullLazyValue.createValue(() -> projectConfigurable));
      final JComponent component = projectConfigurable.createComponent();
      if (component != null) {
        cardPanel.add(component, PROJECT_ITEM_KEY);
        listDataModel.add(0, null);
      }
    }

    for (Module module : modules) {
      myConfigurablesProviders.put(module, AtomicNotNullLazyValue.createValue(() -> {
        final T configurable = createModuleConfigurable(module);
        JComponent component = configurable.createComponent();
        if (component == null) {
          component = new JPanel();
        }
        cardPanel.add(component, module.getName());
        configurable.reset();
        return configurable;
      }));
    }
    moduleList.addListSelectionListener(__ -> showModuleConfigurable(layout, cardPanel, moduleList.getSelectedValue()));

    if (moduleList.getItemsCount() > 0) {
      moduleList.setSelectedIndex(0);
      showModuleConfigurable(layout, cardPanel, listDataModel.getElementAt(0));
    }
    return splitter;
  }

  private void showModuleConfigurable(@NotNull CardLayout layout, @NotNull JPanel cardPanel, @Nullable Module selectedModule) {
    myConfigurablesProviders.get(selectedModule).getValue();
    layout.show(cardPanel, selectedModule == null ? PROJECT_ITEM_KEY : selectedModule.getName());
  }

  protected @Nullable T createDefaultProjectConfigurable() {
    return null;
  }

  /**
   * This configurable is for project-wide settings
   *
   * @return configurable or null if none
   */
  protected @Nullable T createProjectConfigurable() {
    return null;
  }

  /**
   * @return Name for project-wide settings in modules list
   */
  protected @NotNull @NlsContexts.Label String getProjectConfigurableItemName() {
    return myProject.getName();
  }

  /**
   * @return Icon for project-wide sttings in modules list
   */
  protected @Nullable Icon getProjectConfigurableItemIcon() {
    return AllIcons.Nodes.Project;
  }

  protected abstract @NotNull T createModuleConfigurable(Module module);

  @Override
  public boolean isModified() {
    for (AtomicNotNullLazyValue<T> configurableProvider : myConfigurablesProviders.values()) {
      if (configurableProvider.isComputed() && configurableProvider.getValue().isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (AtomicNotNullLazyValue<T> configurableProvider : myConfigurablesProviders.values()) {
      if (configurableProvider.isComputed()) {
        configurableProvider.getValue().apply();
      }
    }
  }

  @Override
  public void reset() {
    for (AtomicNotNullLazyValue<T> configurableProvider : myConfigurablesProviders.values()) {
      if (configurableProvider.isComputed()) {
        configurableProvider.getValue().reset();
      }
    }
  }

  @Override
  public void disposeUIResources() {
    for (AtomicNotNullLazyValue<T> configurableProvider : myConfigurablesProviders.values()) {
      if (configurableProvider.isComputed()) {
        configurableProvider.getValue().disposeUIResources();
      }
    }
    myConfigurablesProviders.clear();
  }

  @Override
  public @NotNull String getId() {
    return getClass().getName();
  }

  protected final @NotNull Project getProject() {
    return myProject;
  }
}
