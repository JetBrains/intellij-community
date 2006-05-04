/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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