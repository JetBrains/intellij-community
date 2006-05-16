package com.intellij.execution.junit2.configuration;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.exclude.SortedComboBoxModel;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConfigurationModuleSelector {
  private final Project myProject;
  private final JComboBox myModulesList;
  private final SortedComboBoxModel<String> myModules = new SortedComboBoxModel<String>(String.CASE_INSENSITIVE_ORDER);

  public ConfigurationModuleSelector(final Project project, final JComboBox modulesList) {
    myProject = project;
    myModulesList = modulesList;
    myModulesList.setModel(myModules);
    myModulesList.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Module module = ModuleManager.getInstance(project).findModuleByName((String)value);
        if (module != null) {
          setIcon(module.getModuleType().getNodeIcon(true));
        }
        return component;
      }
    });
  }

  public void applyTo(final ModuleBasedConfiguration configurationModule) {
    configurationModule.setModuleName((String)myModulesList.getSelectedItem());
  }

  public void reset(final ModuleBasedConfiguration configuration) {
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    final List<Module> list = new ArrayList<Module>();
    for (final Module module : modules) {
      if (isModuleAccepted(module)) list.add(module);
    }
    setModules(list);
    myModules.setSelectedItem(configuration.getConfigurationModule().getModuleName());
  }

  public static boolean isModuleAccepted(final Module module) {
    return ArrayUtil.find(new ModuleType[]{ModuleType.JAVA, ModuleType.WEB, ModuleType.EJB}, module.getModuleType()) != -1;
  }

  public Project getProject() {
    return myProject;
  }

  public RunConfigurationModule getConfigurationModule() {
    final RunConfigurationModule configurationModule = new RunConfigurationModule(getProject(), false);
    configurationModule.setModuleName(myModules.getSelectedItem());
    return configurationModule;
  }

  private void setModules(final Collection<Module> modules) {
    myModules.clear();
    myModules.addAll(ContainerUtil.map(modules, MODULE_NAME));
  }

  private static final Function<Module, String> MODULE_NAME = new Function<Module, String>() {
    public String fun(final Module module) {
      return module.getName();
    }
  };

  public Module getModule() {
    final String moduleName = myModules.getSelectedItem();
    if (moduleName == null) {
      return null;
    }
    return ModuleManager.getInstance(myProject).findModuleByName(moduleName);
  }

  public PsiClass findClass(final String className) {
    return getConfigurationModule().findClass(className);
  }

  public String getModuleName() {
    final String moduleName = myModules.getSelectedItem();
    return moduleName == null ? "" : moduleName;
  }
}
