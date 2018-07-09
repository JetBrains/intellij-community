// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl.ExternalProjectsStateProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

import static com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator.Phase;
import static com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator.getRunConfigurationActivationTaskName;

/**
 * @author Vladislav.Soroka
 * @since 11/14/2014
 */
class ExternalSystemRunManagerListener implements RunManagerListener {
  private Disposable eventDisposable;

  private final ExternalProjectsManagerImpl myManager;
  private final ConcurrentIntObjectMap<Pair<String, RunnerAndConfigurationSettings>> myMap;

  public ExternalSystemRunManagerListener(ExternalProjectsManager manager) {
    myManager = (ExternalProjectsManagerImpl)manager;
    myMap = ContainerUtil.createConcurrentIntObjectMap();
  }

  @Override
  public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
    add(myMap, settings);
  }

  @Override
  public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
    if (settings.getConfiguration() instanceof ExternalSystemRunConfiguration) {
      final Pair<String, RunnerAndConfigurationSettings> pair = myMap.remove(System.identityHashCode(settings));
      if (pair == null) return;

      final ExternalProjectsStateProvider stateProvider = myManager.getStateProvider();
      final ExternalSystemTaskExecutionSettings taskExecutionSettings =
        ((ExternalSystemRunConfiguration)settings.getConfiguration()).getSettings();

      if(taskExecutionSettings.getExternalProjectPath() == null) return;

      final TaskActivationState activation =
        stateProvider.getTasksActivation(taskExecutionSettings.getExternalSystemId(), taskExecutionSettings.getExternalProjectPath());

      for (Phase phase : Phase.values()) {
        for (Iterator<String> iterator = activation.getTasks(phase).iterator(); iterator.hasNext(); ) {
          String task = iterator.next();
          if (pair.first.equals(task)) {
            iterator.remove();
            break;
          }
        }
      }
    }
  }

  @Override
  public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
    if (settings.getConfiguration() instanceof ExternalSystemRunConfiguration) {
      final Pair<String, RunnerAndConfigurationSettings> pair = myMap.get(System.identityHashCode(settings));
      if (pair != null) {
        final ExternalProjectsStateProvider stateProvider = myManager.getStateProvider();
        final ExternalSystemTaskExecutionSettings taskExecutionSettings =
          ((ExternalSystemRunConfiguration)settings.getConfiguration()).getSettings();

        if(taskExecutionSettings.getExternalProjectPath() == null) return;

        final TaskActivationState activation =
          stateProvider.getTasksActivation(taskExecutionSettings.getExternalSystemId(), taskExecutionSettings.getExternalProjectPath());

        for (Phase phase : Phase.values()) {
          final List<String> modifiableActivationTasks = activation.getTasks(phase);
          for (String task : ContainerUtil.newArrayList(modifiableActivationTasks)) {
            if (pair.first.equals(task)) {
              modifiableActivationTasks.remove(task);
              final String runConfigurationActivationTaskName = getRunConfigurationActivationTaskName(settings);
              modifiableActivationTasks.add(runConfigurationActivationTaskName);
              myMap.put(System.identityHashCode(settings), Pair.create(runConfigurationActivationTaskName, settings));
              break;
            }
          }
        }
      }
    }
  }

  public void attach() {
    eventDisposable = Disposer.newDisposable();
    myManager.getProject().getMessageBus().connect(eventDisposable).subscribe(RunManagerListener.TOPIC, this);
  }

  @Override
  public void stateLoaded() {
    myMap.clear();

    for (ExternalSystemManager<?, ?, ?, ?, ?> systemManager : ExternalSystemApiUtil.getAllManagers()) {
      final AbstractExternalSystemTaskConfigurationType configurationType =
        ExternalSystemUtil.findConfigurationType(systemManager.getSystemId());
      if (configurationType == null) continue;
      final List<RunnerAndConfigurationSettings> configurationSettingsList =
        RunManager.getInstance(myManager.getProject()).getConfigurationSettingsList(configurationType);
      for (RunnerAndConfigurationSettings configurationSettings : configurationSettingsList) {
        add(myMap, configurationSettings);
      }
    }
  }

  public void detach() {
    myMap.clear();

    Disposable disposable = eventDisposable;
    if (disposable != null) {
      eventDisposable = null;
      Disposer.dispose(disposable);
    }
  }

  private static void add(@NotNull ConcurrentIntObjectMap<Pair<String, RunnerAndConfigurationSettings>> map,
                          @NotNull RunnerAndConfigurationSettings settings) {
    if (settings.getConfiguration() instanceof ExternalSystemRunConfiguration) {
      map.put(System.identityHashCode(settings), Pair.create(getRunConfigurationActivationTaskName(settings), settings));
    }
  }
}
