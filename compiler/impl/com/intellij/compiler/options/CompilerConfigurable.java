package com.intellij.compiler.options;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class CompilerConfigurable implements SearchableConfigurable, ProjectComponent {
  private CompilerUIConfigurable myDelegateConfigurable;
  private Project myProject;

  public static CompilerConfigurable getInstance(Project project) {
    return project.getComponent(CompilerConfigurable.class);
  }

  public CompilerConfigurable(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
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
    return IconLoader.getIcon("/general/configurableCompiler.png");
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

  public String getComponentName() {
    return "CompilerConfigurable";
  }

  public Runnable showOption(String option) {
    return myDelegateConfigurable.showOption(this, option);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myDelegateConfigurable.clearSearch();
  }
}