// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.startup;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Service(Service.Level.PROJECT)
final class ProjectStartupTaskManager {
  public static final String PREFIX = "Project Startup Tasks: ";

  private final Project myProject;
  private final ProjectStartupSharedConfiguration myShared;
  private final ProjectStartupLocalConfiguration myLocal;

  private void verifyState() {
    if (myShared.isEmpty()) {
      return;
    }

    Collection<RunnerAndConfigurationSettings> sharedConfigurations = getSharedConfigurations();
    List<RunnerAndConfigurationSettings> canNotBeShared = new ArrayList<>();
    Iterator<RunnerAndConfigurationSettings> iterator = sharedConfigurations.iterator();
    while (iterator.hasNext()) {
      final RunnerAndConfigurationSettings configuration = iterator.next();
      if (!configuration.isShared()) {
        iterator.remove();
        canNotBeShared.add(configuration);
      }
    }
    if (!canNotBeShared.isEmpty()) {
      canNotBeShared.addAll(getLocalConfigurations());
      setStartupConfigurations(sharedConfigurations, canNotBeShared);
    }
  }

  ProjectStartupTaskManager(@NotNull Project project) {
    myProject = project;
    myShared = myProject.getService(ProjectStartupSharedConfiguration.class);
    myLocal = myProject.getService(ProjectStartupLocalConfiguration.class);
    verifyState();
  }

  private Collection<RunnerAndConfigurationSettings> getConfigurations(ProjectStartupConfigurationBase configuration) {
    if (configuration.isEmpty()) return Collections.emptyList();

    final List<RunnerAndConfigurationSettings> result = new ArrayList<>();
    final List<ProjectStartupConfigurationBase.ConfigurationDescriptor> list = configuration.getList();
    RunManagerImpl runManager = (RunManagerImpl)RunManager.getInstance(myProject);
    for (ProjectStartupConfigurationBase.ConfigurationDescriptor descriptor : list) {
      final RunnerAndConfigurationSettings settings = runManager.getConfigurationById(descriptor.getId());
      if (settings != null && settings.getName().equals(descriptor.getName())) {
        result.add(settings);
      } else {
        getNotificationGroup().createNotification(
          ExecutionBundle.message("0.run.configuration.1.not.found.removed.from.list", PREFIX, descriptor.getName()),
          MessageType.WARNING).notify(myProject);
      }
    }
    return result;
  }

  public void checkOnChange(RunnerAndConfigurationSettings settings) {
    if (settings.isShared()) {
      return;
    }

    Collection<RunnerAndConfigurationSettings> sharedConfigurations = getSharedConfigurations();
    if (sharedConfigurations.remove(settings)) {
      List<RunnerAndConfigurationSettings> localConfigurations = new ArrayList<>(getLocalConfigurations());
      localConfigurations.add(settings);
      setStartupConfigurations(sharedConfigurations, localConfigurations);

      getNotificationGroup().createNotification(ExecutionBundle.message("0.configuration.was.made.not.shared", PREFIX, settings.getName()),
                                            MessageType.WARNING).notify(myProject);
    }
  }

  public Collection<RunnerAndConfigurationSettings> getSharedConfigurations() {
    return getConfigurations(myShared);
  }

  public Collection<RunnerAndConfigurationSettings> getLocalConfigurations() {
    return getConfigurations(myLocal);
  }

  public void setStartupConfigurations(@NotNull Collection<? extends RunnerAndConfigurationSettings> shared,
                                       @NotNull Collection<? extends RunnerAndConfigurationSettings> local) {
    myShared.setConfigurations(shared);
    myLocal.setConfigurations(local);
  }

  public void rename(final String oldId, RunnerAndConfigurationSettings settings) {
    if (myShared.rename(oldId, settings)) {
      return;
    }
    myLocal.rename(oldId, settings);
  }

  public void delete(final String id) {
    if (myShared.deleteConfiguration(id)) {
      return;
    }
    myLocal.deleteConfiguration(id);
  }

  static NotificationGroup getNotificationGroup() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Project Startup Tasks Messages");
  }

  public boolean isEmpty() {
    return myShared.isEmpty() && myLocal.isEmpty();
  }

  public static @NotNull ProjectStartupTaskManager getInstance(@NotNull Project project) {
    return project.getService(ProjectStartupTaskManager.class);
  }
}
