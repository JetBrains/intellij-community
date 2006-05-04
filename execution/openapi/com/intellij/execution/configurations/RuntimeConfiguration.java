package com.intellij.execution.configurations;

import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.annotations.NonNls;

public abstract class RuntimeConfiguration extends RunConfigurationBase implements LocatableConfiguration, Cloneable {
  protected RuntimeConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(project, factory, name);
  }

  public Module[] getModules() {
    return null;
  }

  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    return null;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
  }


  public RuntimeConfiguration clone() {
    return (RuntimeConfiguration)super.clone();
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(JavaProgramRunner runner) {
    return null;
  }

  public boolean isGeneratedName() {
    return false;
  }

  @NonNls public String suggestedName() {
    return null;
  }
}