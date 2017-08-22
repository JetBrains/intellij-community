/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(name = "ExecutionTargetManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ExecutionTargetManagerImpl extends ExecutionTargetManager implements PersistentStateComponent<Element> {
  @NotNull private final Project myProject;
  @NotNull private final Object myActiveTargetLock = new Object();
  @Nullable private ExecutionTarget myActiveTarget;

  @Nullable private String mySavedActiveTargetId;

  public ExecutionTargetManagerImpl(@NotNull Project project) {
    myProject = project;

    project.getMessageBus().connect().subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings == RunManager.getInstance(myProject).getSelectedConfiguration()) {
          updateActiveTarget(settings);
        }
      }

      @Override
      public void runConfigurationSelected() {
        updateActiveTarget();
      }
    });
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    synchronized (myActiveTargetLock) {
      String id = myActiveTarget == null ? mySavedActiveTargetId : myActiveTarget.getId();
      if (id != null && !id.equals(DefaultExecutionTarget.INSTANCE.getId())) {
        state.setAttribute("SELECTED_TARGET", id);
      }
    }
    return state;
  }

  @Override
  public void loadState(Element state) {
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null && mySavedActiveTargetId == null) {
        mySavedActiveTargetId = state.getAttributeValue("SELECTED_TARGET");
      }
    }
  }

  @NotNull
  @Override
  public ExecutionTarget getActiveTarget() {
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null) {
        updateActiveTarget();
      }
      return myActiveTarget;
    }
  }

  @Override
  public void setActiveTarget(@NotNull ExecutionTarget target) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (myActiveTargetLock) {
      updateActiveTarget(RunManager.getInstance(myProject).getSelectedConfiguration(), target);
    }
  }

  private void updateActiveTarget() {
    updateActiveTarget(RunManager.getInstance(myProject).getSelectedConfiguration());
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings) {
    updateActiveTarget(settings, null);
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings, @Nullable ExecutionTarget toSelect) {
    List<ExecutionTarget> suitable = settings == null ? Collections.singletonList(DefaultExecutionTarget.INSTANCE)
                                                      : getTargetsFor(settings);
    ExecutionTarget toNotify;
    synchronized (myActiveTargetLock) {
      if (toSelect == null) toSelect = myActiveTarget;

      int index = -1;
      if (toSelect != null) {
        index = suitable.indexOf(toSelect);
      }
      else if (mySavedActiveTargetId != null) {
        for (int i = 0, size = suitable.size(); i < size; i++) {
          if (suitable.get(i).getId().equals(mySavedActiveTargetId)) {
            index = i;
            break;
          }
        }
      }
      toNotify =
        doSetActiveTarget(index >= 0 ? suitable.get(index) : getDefaultTarget(suitable));
    }

    if (toNotify != null) {
      myProject.getMessageBus().syncPublisher(TOPIC).activeTargetChanged(toNotify);
    }
  }

  private static ExecutionTarget getDefaultTarget(List<ExecutionTarget> suitable){
    ExecutionTarget result = ContainerUtil.find(suitable, ExecutionTarget::isReady);
    return  result != null ? result : DefaultExecutionTarget.INSTANCE;
  }

  @Nullable
  private ExecutionTarget doSetActiveTarget(@NotNull ExecutionTarget newTarget) {
    mySavedActiveTargetId = null;

    ExecutionTarget prev = myActiveTarget;
    myActiveTarget = newTarget;
    if (prev != null && !prev.equals(myActiveTarget)) {
      return myActiveTarget;
    }
    return null;
  }

  @NotNull
  @Override
  public List<ExecutionTarget> getTargetsFor(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings == null) {
      return Collections.emptyList();
    }

    List<ExecutionTarget> result = new ArrayList<>();
    for (ExecutionTargetProvider eachTargetProvider : Extensions.getExtensions(ExecutionTargetProvider.EXTENSION_NAME)) {
      for (ExecutionTarget eachTarget : eachTargetProvider.getTargets(myProject, settings)) {
        if (canRun(settings, eachTarget)) {
          result.add(eachTarget);
        }
      }
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public void update() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    updateActiveTarget();
  }
}
