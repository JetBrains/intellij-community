/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testDiscovery;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public abstract class TestDiscoveryConfiguration extends JavaTestConfigurationBase {
  private String myChangeList;
  private Pair<String, String> myPosition;

  protected JavaTestConfigurationBase myDelegate;

  public TestDiscoveryConfiguration(String name,
                                    @NotNull JavaRunConfigurationModule configurationModule,
                                    @NotNull ConfigurationFactory factory, 
                                    JavaTestConfigurationBase delegate) {
    super(name, configurationModule, factory);
    myDelegate = delegate;
  }

  @Override
  public void setVMParameters(String value) {
    myDelegate.setVMParameters(value);
  }

  @Override
  public String getVMParameters() {
    return myDelegate.getVMParameters();
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    return myDelegate.isAlternativeJrePathEnabled();
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    myDelegate.setAlternativeJrePathEnabled(enabled);
  }

  @Override
  @Nullable
  public String getAlternativeJrePath() {
    return myDelegate.getAlternativeJrePath();
  }

  @Override
  public void setAlternativeJrePath(String path) {
    myDelegate.setAlternativeJrePath(path);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (myPosition == null &&
        myChangeList != null && ChangeListManager.getInstance(getProject()).findChangeList(myChangeList) == null) {
      throw new RuntimeConfigurationException("Change list " + myChangeList + " doesn't exist");
    }
    if (myPosition != null) {
      if (StringUtil.isEmptyOrSpaces(myPosition.first)) {
        throw new RuntimeConfigurationException("No class specified");
      }
      if (StringUtil.isEmptyOrSpaces(myPosition.second)) {
        throw new RuntimeConfigurationException("No method specified");
      }
    }
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  public Collection<Module> getValidModules() {
    return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<TestDiscoveryConfiguration> group = new SettingsEditorGroup<TestDiscoveryConfiguration>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"),
                    new TestDiscoveryConfigurable<TestDiscoveryConfiguration>(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<TestDiscoveryConfiguration>());
    return group;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myDelegate.readExternal(element);
    super.readExternal(element);
    readModule(element);

    final String classQName = element.getAttributeValue("class");
    final String methodName = element.getAttributeValue("method");
    myPosition = classQName != null && methodName != null ? Pair.create(classQName, methodName) : null;
    myChangeList = element.getAttributeValue("changeList");
    if ("All".equals(myChangeList)) {
      myChangeList = null;
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    myDelegate.writeExternal(element);
    super.writeExternal(element);

    writeModule(element);
    
    if (myPosition != null) {
      element.setAttribute("class", myPosition.first);
      element.setAttribute("method", myPosition.second);
    }
    element.setAttribute("changeList", myChangeList == null ? "All" : myChangeList);
  }


  @Nullable
  @Override
  public String getRunClass() {
    return null;
  }

  @Nullable
  @Override
  public String getPackage() {
    return "";
  }

  @Override
  public void setProgramParameters(@Nullable String value) {
    myDelegate.setProgramParameters(value);
  }

  @Override
  @Nullable
  public String getProgramParameters() {
    return myDelegate.getProgramParameters();
  }

  @Override
  public void setWorkingDirectory(@Nullable String value) {
    myDelegate.setWorkingDirectory(value);
  }

  @Override
  @Nullable
  public String getWorkingDirectory() {
    return myDelegate.getWorkingDirectory();
  }

  @Override
  public void setEnvs(@NotNull Map<String, String> envs) {
    myDelegate.setEnvs(envs);
  }

  @Override
  @NotNull
  public Map<String, String> getEnvs() {
    return myDelegate.getEnvs();
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    myDelegate.setPassParentEnvs(passParentEnvs);
  }

  @Override
  public boolean isPassParentEnvs() {
    return myDelegate.isPassParentEnvs();
  }

  @Override
  public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
    return myDelegate.createTestConsoleProperties(executor);
  }

  public void setPosition(Pair<String, String> position) {
    myPosition = position;
  }

  public void setChangeList(String changeList) {
    myChangeList = changeList;
  }

  public Pair<String, String> getPosition() {
    return myPosition;
  }

  public String getChangeList() {
    return myChangeList;
  }
}
