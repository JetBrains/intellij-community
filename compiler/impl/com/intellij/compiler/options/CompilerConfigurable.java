package com.intellij.compiler.options;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CompilerConfigurable implements SearchableConfigurable {
  private CompilerUIConfigurable myDelegateConfigurable;
  private Project myProject;
  private static final Icon ICON = IconLoader.getIcon("/general/configurableCompiler.png");

  public static CompilerConfigurable getInstance(Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, CompilerConfigurable.class);
  }

  public CompilerConfigurable(Project project) {
    myProject = project;
  }

  public String getDisplayName() {
    return CompilerBundle.message("compiler.configurable.display.name");
  }

  public boolean isModified() {
    return myDelegateConfigurable != null && myDelegateConfigurable.isModified();
  }

  public void reset() {
    if (myDelegateConfigurable != null) {
      myDelegateConfigurable.reset();
    }
  }

  public void apply() throws ConfigurationException {
    if (myDelegateConfigurable != null) {
      myDelegateConfigurable.apply();
    }
  }

  public Icon getIcon() {
    return ICON;
  }

  public JComponent createComponent() {
    myDelegateConfigurable = new CompilerUIConfigurable(myProject);
    return myDelegateConfigurable.createComponent();
  }

  public void disposeUIResources() {
    myDelegateConfigurable = null;
  }

  public String getHelpTopic() {
    return "project.propCompiler";
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}