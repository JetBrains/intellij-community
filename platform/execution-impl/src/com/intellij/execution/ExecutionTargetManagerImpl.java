// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.TargetAwareRunProfile;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.function.BiPredicate;

@ApiStatus.Internal
@State(name = "ExecutionTargetManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ExecutionTargetManagerImpl extends ExecutionTargetManager implements PersistentStateComponent<Element> {
  public static final ExecutionTarget MULTIPLE_TARGETS = new ExecutionTarget() {
    @Override
    public @NotNull String getId() {
      return "multiple_targets";
    }

    @Override
    public @NotNull String getDisplayName() {
      return ExecutionBundle.message("multiple.specified");
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public boolean canRun(@NotNull RunConfiguration configuration) {
      return true;
    }
  };

  private final @NotNull Project myProject;
  private final @NotNull Object myActiveTargetLock = new Object();
  private @Nullable ExecutionTarget myActiveTarget;

  private @Nullable String mySavedActiveTargetId;

  public ExecutionTargetManagerImpl(@NotNull Project project) {
    myProject = project;

    project.getMessageBus().connect().subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings == getRunManager().getSelectedConfiguration()) {
          updateActiveTarget(settings);
        }
      }

      @Override
      public void runConfigurationSelected(@Nullable RunnerAndConfigurationSettings settings) {
        updateActiveTarget(settings);
      }
    });
  }

  private @Nullable RunManagerImpl myRunManager;

  private @NotNull RunManagerImpl getRunManager() {
    RunManagerImpl runManager = myRunManager;
    if (runManager == null) {
      runManager = RunManagerImpl.getInstanceImpl(myProject);
      myRunManager = runManager;
    }
    return runManager;
  }

  @TestOnly
  public void setRunManager(@NotNull RunManagerImpl runManager) {
    myRunManager = runManager;
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    synchronized (myActiveTargetLock) {
      if (mySavedActiveTargetId != null && !mySavedActiveTargetId.equals(DefaultExecutionTarget.INSTANCE.getId())) {
        state.setAttribute("SELECTED_TARGET", mySavedActiveTargetId);
      }
    }
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null && mySavedActiveTargetId == null) {
        mySavedActiveTargetId = state.getAttributeValue("SELECTED_TARGET");
      }
    }
  }

  @Override
  public @NotNull ExecutionTarget getActiveTarget() {
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null) {
        updateActiveTarget();
      }
      return myActiveTarget;
    }
  }

  @Override
  public void setActiveTarget(@NotNull ExecutionTarget target) {
    ThreadingAssertions.assertEventDispatchThread();
    synchronized (myActiveTargetLock) {
      updateActiveTarget(getRunManager().getSelectedConfiguration(), target);
    }
  }

  private void updateActiveTarget() {
    updateActiveTarget(getRunManager().getSelectedConfiguration());
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings) {
    updateActiveTarget(settings, null);
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings, @Nullable ExecutionTarget toSelect) {
    List<ExecutionTarget> suitable = settings == null ? Collections.singletonList(DefaultExecutionTarget.INSTANCE)
                                                      : getTargetsFor(settings.getConfiguration());
    ExecutionTarget toNotify;
    synchronized (myActiveTargetLock) {
      if (toSelect == null && !DefaultExecutionTarget.INSTANCE.equals(myActiveTarget)) {
        toSelect = myActiveTarget;
      }

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

  private ExecutionTarget getDefaultTarget(List<? extends ExecutionTarget> suitable){
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

  private @Nullable ExecutionTarget doSetActiveTarget(@NotNull ExecutionTarget newTarget) {
    if (!DefaultExecutionTarget.INSTANCE.equals(newTarget)) {
      mySavedActiveTargetId = newTarget.getId();
    }

    ExecutionTarget prev = myActiveTarget;
    myActiveTarget = newTarget;
    if (prev != null && !prev.equals(myActiveTarget)) {
      return myActiveTarget;
    }
    return null;
  }

  @Override
  public boolean doCanRun(@Nullable RunConfiguration configuration, @NotNull ExecutionTarget target) {
    if (configuration == null) {
      return false;
    }

    boolean isCompound = configuration instanceof CompoundRunConfiguration;
    if (isCompound && target == MULTIPLE_TARGETS) {
      return true;
    }

    ExecutionTarget defaultTarget = DefaultExecutionTarget.INSTANCE;
    boolean checkFallbackToDefault = isCompound && !target.equals(defaultTarget);

    return doWithEachNonCompoundWithSpecifiedTarget(configuration, (subConfiguration, executionTarget) -> {
      if (!(subConfiguration instanceof TargetAwareRunProfile targetAwareProfile)) {
        return true;
      }

      if ((Registry.is("update.run.configuration.actions.from.cache", false))
          && getRunManager().isInvalidInCache(subConfiguration)) {
        return false;
      }

      return target.canRun(subConfiguration) && targetAwareProfile.canRunOn(target)
             || (checkFallbackToDefault && defaultTarget.canRun(subConfiguration) && targetAwareProfile.canRunOn(defaultTarget));
    });
  }

  @Override
  public @NotNull List<ExecutionTarget> getTargetsFor(@Nullable RunConfiguration configuration) {
    if (configuration == null) {
      return Collections.emptyList();
    }

    List<ExecutionTargetProvider> providers = ExecutionTargetProvider.EXTENSION_NAME.getExtensionList();
    LinkedHashSet<ExecutionTarget> result = new LinkedHashSet<>();

    Set<ExecutionTarget> specifiedTargets = new HashSet<>();
    doWithEachNonCompoundWithSpecifiedTarget(configuration, (subConfiguration, executionTarget) -> {
      for (ExecutionTargetProvider eachTargetProvider : providers) {
        List<ExecutionTarget> supportedTargets = eachTargetProvider.getTargets(myProject, subConfiguration);
        if (executionTarget == null) {
          result.addAll(supportedTargets);
        }
        else if (supportedTargets.contains(executionTarget)) {
          result.add(executionTarget);
          specifiedTargets.add(executionTarget);
          break;
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
    return ContainerUtil.filter(result, it -> doCanRun(configuration, it));
  }

  private boolean doWithEachNonCompoundWithSpecifiedTarget(@NotNull RunConfiguration configuration, @NotNull BiPredicate<? super RunConfiguration, ? super ExecutionTarget> action) {
    Set<RunConfiguration> recursionGuard = new HashSet<>();
    LinkedList<Pair<RunConfiguration, ExecutionTarget>> toProcess = new LinkedList<>();
    toProcess.add(Pair.create(configuration, null));

    while (!toProcess.isEmpty()) {
      Pair<RunConfiguration, ExecutionTarget> eachWithTarget = toProcess.pollFirst();
      assert eachWithTarget != null;
      if (!recursionGuard.add(eachWithTarget.first)) {
        continue;
      }

      RunConfiguration eachConfiguration = eachWithTarget.first;
      if (eachConfiguration instanceof CompoundRunConfiguration) {
        for (Map.Entry<RunConfiguration, ExecutionTarget> subConfigWithTarget : ((CompoundRunConfiguration)eachConfiguration).getConfigurationsWithTargets(getRunManager()).entrySet()) {
          toProcess.add(Pair.create(subConfigWithTarget.getKey(), subConfigWithTarget.getValue()));
        }
      }
      else if (!action.test(eachWithTarget.first, eachWithTarget.second)) {
        return false;
      }
    }
    return true;
  }

  public @Nullable ExecutionTarget findTargetByIdFor(@Nullable RunConfiguration configuration, @Nullable String id) {
    if (id == null) {
      return null;
    }
    else {
      return ContainerUtil.find(getTargetsFor(configuration), it -> it.getId().equals(id));
    }
  }

  @Override
  public void update() {
    ThreadingAssertions.assertEventDispatchThread();
    updateActiveTarget();
  }

  @TestOnly
  public void reset(@Nullable RunManagerImpl runManager) {
    mySavedActiveTargetId = null;
    myActiveTarget = null;
    myRunManager = runManager;
  }
}
