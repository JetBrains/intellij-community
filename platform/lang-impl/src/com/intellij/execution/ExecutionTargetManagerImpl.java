// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.TargetAwareRunProfile;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

@State(name = "ExecutionTargetManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ExecutionTargetManagerImpl extends ExecutionTargetManager implements PersistentStateComponent<Element> {
  public static final ExecutionTarget MULTIPLE_TARGETS = new ExecutionTarget() {
    @NotNull
    @Override
    public String getId() {
      return "multiple_targets";
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return "Multiple specified";
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public boolean canRun(@NotNull RunnerAndConfigurationSettings configuration) {
      return true;
    }
  };
  
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

  private ExecutionTarget getDefaultTarget(List<ExecutionTarget> suitable){
    // The following cases are possible when we enter this method:
    // a) mySavedActiveTargetId == null. It means that we open / import project for the first time and there is no target selected
    // In this case we are trying to find the first ExecutionTarget that is ready, because we do not have any other conditions.
    // b) mySavedActiveTargetId != null. It means that some target was saved, but we weren't able to find it. Right now it can happen
    // when and only when there was a device connected, it was saved as a target, next the device was disconnected and other device was
    // connected / no devices left connected. In this case we should not select the target that is ready, cause most probably user still
    // needs some device to be selected (or at least the device placeholder). As all the devices and device placeholders are always shown
    // at the beginning of the list, selecting the first item works in this case.
    ExecutionTarget result = mySavedActiveTargetId == null ? ContainerUtil.find(suitable, ExecutionTarget::isReady) : ContainerUtil.getFirstItem(suitable);
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

  protected boolean doCanRun(@Nullable RunnerAndConfigurationSettings settings, @NotNull ExecutionTarget target) {
    if (settings == null) return false;

    boolean isCompound = settings.getConfiguration() instanceof CompoundRunConfiguration;
    if (isCompound && target == MULTIPLE_TARGETS) return true;

    ExecutionTarget defaultTarget = DefaultExecutionTarget.INSTANCE;
    boolean checkFallbackToDefault = isCompound
                                     && !target.equals(defaultTarget);

    return doWithEachNonCompoundWithSpecifiedTarget(settings, each -> {
      RunConfiguration configuration = each.first.getConfiguration();
      if (!(configuration instanceof TargetAwareRunProfile)) return true;
      TargetAwareRunProfile targetAwareProfile = (TargetAwareRunProfile)configuration;

      return target.canRun(each.first) && targetAwareProfile.canRunOn(target)
             || (checkFallbackToDefault && defaultTarget.canRun(each.first) && targetAwareProfile.canRunOn(defaultTarget));
    });
  }

  @NotNull
  @Override
  public List<ExecutionTarget> getTargetsFor(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings == null) {
      return Collections.emptyList();
    }

    ExecutionTargetProvider[] providers = Extensions.getExtensions(ExecutionTargetProvider.EXTENSION_NAME);
    LinkedHashSet<ExecutionTarget> result = new LinkedHashSet<>();

    Set<ExecutionTarget> specifiedTargets = new THashSet<>();
    doWithEachNonCompoundWithSpecifiedTarget(settings, each -> {
      for (ExecutionTargetProvider eachTargetProvider : providers) {
        List<ExecutionTarget> supportedTargets = eachTargetProvider.getTargets(myProject, each.first);

        if (each.second != null) {
          if (supportedTargets.contains(each.second)) {
            result.add(each.second);
            specifiedTargets.add(each.second);
            break;
          }
        }
        else {
          result.addAll(supportedTargets);
        }
      }
      return true;
    });

    if (!result.isEmpty()) {
      specifiedTargets.forEach(it -> result.retainAll(Collections.singleton(it)));
      if (result.isEmpty()) {
        result.add(MULTIPLE_TARGETS);
      }
    }
    return Collections.unmodifiableList(ContainerUtil.filter(result, it -> doCanRun(settings, it)));
  }

  private boolean doWithEachNonCompoundWithSpecifiedTarget(
    @Nullable RunnerAndConfigurationSettings settings, @NotNull Processor<Pair<RunnerAndConfigurationSettings, ExecutionTarget>> action) {
    if (settings == null) return true;

    RunManagerImpl runManager = (RunManagerImpl)RunManager.getInstance(myProject);

    Set<RunnerAndConfigurationSettings> recursionGuard = new THashSet<>();
    LinkedList<Pair<RunnerAndConfigurationSettings, ExecutionTarget>> toProcess = new LinkedList<>();
    toProcess.add(Pair.create(settings, null));

    while (!toProcess.isEmpty()) {
      Pair<RunnerAndConfigurationSettings, ExecutionTarget> eachWithTarget = toProcess.pollFirst();
      if (!recursionGuard.add(eachWithTarget.first)) continue;

      RunConfiguration eachConfiguration = eachWithTarget.first.getConfiguration();
      if (eachConfiguration instanceof CompoundRunConfiguration) {
        for (Map.Entry<RunConfiguration, ExecutionTarget> subConfigWithTarget
          : ((CompoundRunConfiguration)eachConfiguration).getConfigurationsWithTargets().entrySet()) {
          RunnerAndConfigurationSettingsImpl subSettings = runManager.getSettings(subConfigWithTarget.getKey());
          if (subSettings != null /* it might have been already deleted */) {
            toProcess.add(Pair.create(subSettings, subConfigWithTarget.getValue()));
          }
        }
      }
      else {
        if (!action.process(Pair.create(eachWithTarget.first, eachWithTarget.second))) return false;
      }
    }
    return true;
  }

  @Nullable
  public ExecutionTarget findTargetByIdFor(@Nullable RunnerAndConfigurationSettings settings, @Nullable String id) {
    if (id == null) return null;
    return ContainerUtil.find(getTargetsFor(settings), it -> it.getId().equals(id));
  }

  @Override
  public void update() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    updateActiveTarget();
  }
  
  @TestOnly
  public void reset() {
    mySavedActiveTargetId = null;
    myActiveTarget = null;
  }
}
