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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupConfiguration {
  private final Project myProject;
  private final ProjectStartupSharedConfiguration myShared;
  private final ProjectStartupLocalConfiguration myLocal;
  private final RunManagerEx myRunManager;

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
      myLocal.clear();
      return fillResult(result, myShared.getList());
    }
    return fillResult(result, myLocal.getList());
  }

  private List<RunnerAndConfigurationSettings> fillResult(List<RunnerAndConfigurationSettings> result, List<String> list) {
    for (String s : list) {
      final RunnerAndConfigurationSettings settings = myRunManager.findConfigurationByName(s);
      if (settings != null) {
        result.add(settings);
      }
    }
    return result;
  }

  public void setStartupConfigurations(final @NotNull List<RunnerAndConfigurationSettings> list, final boolean shared) {
    final List<String> names = ContainerUtil.map(list, new Function<RunnerAndConfigurationSettings, String>() {
      @Override
      public String fun(RunnerAndConfigurationSettings settings) {
        return settings.getName();
      }
    });
    if (shared) {
      myLocal.clear();
      myShared.setList(names);
    } else {
      myShared.clear();
      myLocal.setList(names);
    }
  }

  public boolean canBeShared() {
    if (isShared()) return true;
    if (isEmpty()) return true;

    final List<String> list = myLocal.getList();
    for (String s : list) {
      final RunnerAndConfigurationSettings settings = myRunManager.findConfigurationByName(s);
      if (settings != null) {
        if (! myRunManager.isConfigurationShared(settings)) return false;
      }
    }
    return true;
  }

  public boolean isShared() {
    return ! myShared.isEmpty();
  }

  public boolean isEmpty() {
    return myShared.isEmpty() && myLocal.isEmpty();
  }
}
