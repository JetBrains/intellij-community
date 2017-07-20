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
package com.intellij.execution.compound;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class CompoundRunConfiguration extends RunConfigurationBase implements RunnerIconProvider, WithoutOwnBeforeRunSteps, Cloneable {
  static final Comparator<RunConfiguration> COMPARATOR = (o1, o2) -> {
    int i = o1.getType().getDisplayName().compareTo(o2.getType().getDisplayName());
    return (i != 0) ? i : o1.getName().compareTo(o2.getName());
  };
  private Set<Pair<String, String>> myPairs = new HashSet<>();
  private Set<RunConfiguration> mySetToRun = new TreeSet<>(COMPARATOR);
  private boolean myInitialized = false;

  public CompoundRunConfiguration(Project project, @NotNull CompoundRunConfigurationType type, String name) {
    super(project, type.getConfigurationFactories()[0], name);
  }

  public Set<RunConfiguration> getSetToRun() {
    initIfNeed();
    return mySetToRun;
  }

  private void initIfNeed() {
    if (myInitialized) return;
    mySetToRun.clear();
    RunManagerImpl manager = RunManagerImpl.getInstanceImpl(getProject());
    for (Pair<String, String> pair : myPairs) {
      RunnerAndConfigurationSettings settings = manager.findConfigurationByTypeAndName(pair.first, pair.second);
      if (settings != null && settings.getConfiguration() != this) {
        mySetToRun.add(settings.getConfiguration());
      }
    }
    myInitialized = true;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new CompoundRunConfigurationSettingsEditor(getProject());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (getSetToRun().isEmpty()) throw new RuntimeConfigurationException("There is nothing to run");
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull final ExecutionEnvironment environment) throws ExecutionException {
    try {
      checkConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      throw new ExecutionException(e.getMessage());
    }
    return new RunProfileState() {
      @Nullable
      @Override
      public ExecutionResult execute(final Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ApplicationManager.getApplication().invokeLater(() -> {
          RunManagerImpl manager = RunManagerImpl.getInstanceImpl(getProject());
          for (RunConfiguration configuration : getSetToRun()) {
            RunnerAndConfigurationSettings settings = manager.getSettings(configuration);
            if (settings != null) {
              ExecutionUtil.runConfiguration(settings, executor);
            }
          }
        });
        return null;
      }
    };
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myPairs.clear();
    List<Element> children = element.getChildren("toRun");
    for (Element child : children) {
      String type = child.getAttributeValue("type");
      String name = child.getAttributeValue("name");
      if (type != null && name != null) {
        myPairs.add(Pair.create(type, name));
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    for (RunConfiguration configuration : getSetToRun()) {
      Element child = new Element("toRun");
      child.setAttribute("type", configuration.getType().getId());
      child.setAttribute("name", configuration.getName());
      element.addContent(child);
    }
  }

  @Override
  public RunConfiguration clone() {
    CompoundRunConfiguration clone = (CompoundRunConfiguration)super.clone();
    clone.myPairs = new HashSet<>();
    clone.myPairs.addAll(myPairs);
    clone.mySetToRun = new TreeSet<>(COMPARATOR);
    clone.mySetToRun.addAll(getSetToRun());
    return clone;
  }


  @Nullable
  @Override
  public Icon getExecutorIcon(@NotNull RunConfiguration configuration, @NotNull Executor executor) {
    if (DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId()) && hasRunningSingletones()) {
      return AllIcons.Actions.Restart;
    }
    return executor.getIcon();
  }

  protected boolean hasRunningSingletones() {
    Project project = getProject();
    if (project.isDisposed()) return false;
    final ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(project);

    return executionManager.getRunningDescriptors(s -> {
      RunManagerImpl manager = RunManagerImpl.getInstanceImpl(project);
      for (RunConfiguration runConfiguration : mySetToRun) {
        if (runConfiguration instanceof CompoundRunConfiguration && ((CompoundRunConfiguration)runConfiguration).hasRunningSingletones()) {
          return true;
        }
        RunnerAndConfigurationSettings settings =
          manager.findConfigurationByTypeAndName(runConfiguration.getType().getId(), runConfiguration.getName());
        if (settings != null && settings.isSingleton() && runConfiguration.equals(s.getConfiguration())) {
          return true;
        }
      }
      return false;
    }).size() > 0;
  }
}
