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
package com.intellij.execution.startup;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupConfiguration {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("Project Startup Tasks Messages");
  @NonNls public static final String PREFIX = "Project Startup Tasks: ";
  private final Project myProject;
  private final ProjectStartupSharedConfiguration myShared;
  private final ProjectStartupLocalConfiguration myLocal;
  private final RunManagerEx myRunManager;
  private boolean myInitialized;

  public static ProjectStartupConfiguration getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, ProjectStartupConfiguration.class);
  }

  public ProjectStartupConfiguration(Project project, ProjectStartupSharedConfiguration shared, ProjectStartupLocalConfiguration local,
                                     @NotNull RunManager runManager) {
    myProject = project;
    myShared = shared;
    myLocal = local;
    myRunManager = (RunManagerEx)runManager;
  }

  public List<RunnerAndConfigurationSettings> getStartupConfigurations() {
    final List<RunnerAndConfigurationSettings> result = new ArrayList<RunnerAndConfigurationSettings>();
    if (! myShared.isEmpty()) {
      if (! myInitialized) {
        myLocal.shared();
        myInitialized = true;
      }
      myLocal.clear();
      return fillResult(result, myShared.getList(), true);
    }
    return fillResult(result, myLocal.getList(), false);
  }

  public void rename(final String oldId, RunnerAndConfigurationSettings settings) {
    if (! myShared.isEmpty()) {
      renameInStorage(oldId, settings, myShared);
    } else {
      renameInStorage(oldId, settings, myLocal);
    }
  }

  private static void renameInStorage(String oldId, RunnerAndConfigurationSettings settings, ProjectStartupConfigurationBase configuration) {
    final List<ProjectStartupConfigurationBase.ConfigurationDescriptor> list = configuration.getList();
    for (ProjectStartupConfigurationBase.ConfigurationDescriptor descriptor : list) {
      if (descriptor.getId().equals(oldId)) {
        final List<ProjectStartupConfigurationBase.ConfigurationDescriptor> newList =
          new ArrayList<ProjectStartupConfigurationBase.ConfigurationDescriptor>(list);
        newList.remove(descriptor);
        newList.add(new ProjectStartupConfigurationBase.ConfigurationDescriptor(settings.getUniqueID(), settings.getName()));
        configuration.setList(newList);
        return;
      }
    }
  }

  public void delete(final String name) {
    if (! myShared.isEmpty()) {
      deleteInStorage(name, myShared);
    } else {
      deleteInStorage(name, myLocal);
    }
  }

  private static void deleteInStorage(String name, ProjectStartupConfigurationBase configuration) {
    final List<ProjectStartupConfigurationBase.ConfigurationDescriptor> list = configuration.getList();
    final Iterator<ProjectStartupConfigurationBase.ConfigurationDescriptor> iterator = list.iterator();
    while (iterator.hasNext()) {
      final ProjectStartupConfigurationBase.ConfigurationDescriptor descriptor = iterator.next();
      if (descriptor.getName().equals(name)) {
        iterator.remove();
        return;
      }
    }
  }

  private List<RunnerAndConfigurationSettings> fillResult(List<RunnerAndConfigurationSettings> result,
                                                          List<ProjectStartupConfigurationBase.ConfigurationDescriptor> list,
                                                          boolean shared) {
    boolean changed = false;
    for (ProjectStartupConfigurationBase.ConfigurationDescriptor descriptor : list) {
      final RunnerAndConfigurationSettings settings = myRunManager.findConfigurationByName(descriptor.getName());
      if (settings != null) {
        result.add(settings);
        if (! settings.getUniqueID().equals(descriptor.getId())) {
          changed = true;
        }
      } else {
        changed = true;
        NOTIFICATION_GROUP.createNotification(PREFIX + " Run Configuration '" + descriptor.getName() + "' not found, removed from list.",
                                              MessageType.WARNING).notify(myProject);
      }
    }
    if (changed) {
      setStartupConfigurations(result, shared);
    }
    return result;
  }

  public void setStartupConfigurations(final @NotNull List<RunnerAndConfigurationSettings> list, final boolean shared) {
    final List<ProjectStartupConfigurationBase.ConfigurationDescriptor> names =
      ContainerUtil.map(list, new Function<RunnerAndConfigurationSettings, ProjectStartupConfigurationBase.ConfigurationDescriptor>() {
      @Override
      public ProjectStartupConfigurationBase.ConfigurationDescriptor fun(RunnerAndConfigurationSettings settings) {
        return new ProjectStartupConfigurationBase.ConfigurationDescriptor(settings.getUniqueID(), settings.getName());
      }
    });
    if (shared) {
      myLocal.clear();
      myLocal.shared();
      myShared.setList(names);
    } else {
      myShared.clear();
      myLocal.setList(names);
      myLocal.local();
    }
  }

  public boolean canBeShared() {
    if (isShared()) return true;
    if (isEmpty()) return true;

    final List<ProjectStartupConfigurationBase.ConfigurationDescriptor> list = myLocal.getList();
    for (ProjectStartupConfigurationBase.ConfigurationDescriptor descriptor : list) {
      final RunnerAndConfigurationSettings settings = myRunManager.findConfigurationByName(descriptor.getName());
      if (settings != null) {
        if (! myRunManager.isConfigurationShared(settings)) return false;
      }
    }
    return true;
  }

  public boolean isShared() {
    return myLocal.isShared();
  }

  public boolean isEmpty() {
    return myShared.isEmpty() && myLocal.isEmpty();
  }

  public void checkOnChange(RunnerAndConfigurationSettings settings) {
    if (! isShared()) return;
    if (! myRunManager.isConfigurationShared(settings)) {
      myLocal.local();
      NOTIFICATION_GROUP.createNotification(PREFIX + " configuration was made \"not shared\", since included Run Configuration '" +
        settings.getName() + "' is not shared.", MessageType.WARNING).notify(myProject);
    }
  }
}
