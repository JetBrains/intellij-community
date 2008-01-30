package com.intellij.execution.impl;

import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class RunnerRegistryImpl extends RunnerRegistry {
  private final List<ProgramRunner> myRunnersOrder = new ArrayList<ProgramRunner>();
  @NonNls public static final String RUN_CONTEXT_GROUP = "RunContextGroup";
  private ActionManager myManager;
  private Map<ProgramRunner, AnAction> myRunnerToContextAction = new HashMap<ProgramRunner, AnAction>();

  public RunnerRegistryImpl(@NotNull ActionManager manager) {
    myManager = manager;
  }

  @NotNull
  public String getComponentName() {
    return "RunnerRegistryImpl";
  }

  public boolean hasRunner(@NotNull final String executorId, @NotNull final RunProfile settings) {
    final ProgramRunner[] runners = getRegisteredRunners();
    for (final ProgramRunner runner : runners) {
      if (runner.canRun(executorId, settings)) {
        return true;
      }
    }

    return false;
  }

  public ProgramRunner getRunner(final String executorId, final RunProfile settings) {
    final ProgramRunner[] runners = getRegisteredRunners();
    for (final ProgramRunner runner : runners) {
      if (runner.canRun(executorId, settings)) {
        return runner;
      }
    }

    return null;
  }

  public void initComponent() {
    final ProgramRunner[] runners = Extensions.getExtensions(ProgramRunner.PROGRAM_RUNNER_EP);
    for (ProgramRunner runner : runners) {
      registerRunner(runner);
    }
  }

  public synchronized void disposeComponent() {
    while (myRunnersOrder.size() > 0) {
      final ProgramRunner runner = myRunnersOrder.get(myRunnersOrder.size() - 1);
      unregisterRunner(runner);
    }
  }

  public void registerRunner(final ProgramRunner runner) {
    if (myRunnersOrder.contains(runner)) return;
    myRunnersOrder.add(runner);

    final String contextActionId = runner.getInfo().getRunContextActionId();
    AnAction contextAction = myManager.getAction(contextActionId);

    if (contextAction == null) {
      contextAction = new RunContextAction(runner);
      myManager.registerAction(contextActionId, contextAction);
      myRunnerToContextAction.put(runner, contextAction);

      final DefaultActionGroup group = (DefaultActionGroup) myManager.getAction(RUN_CONTEXT_GROUP);
      group.add(contextAction);
    }
  }

  public void unregisterRunner(final ProgramRunner runner) {
    myRunnersOrder.remove(runner);

    final DefaultActionGroup group = (DefaultActionGroup) myManager.getAction(RUN_CONTEXT_GROUP);
    group.remove(myManager.getAction(runner.getInfo().getRunContextActionId()));

    final AnAction contextAction = myRunnerToContextAction.get(runner);
    if (contextAction != null) {
      myManager.unregisterAction(runner.getInfo().getRunContextActionId());
      myRunnerToContextAction.remove(runner);
    }
  }

  public ProgramRunner[] getRegisteredRunners() {
    return myRunnersOrder.toArray(new ProgramRunner[myRunnersOrder.size()]);
  }

  @Nullable
  public ProgramRunner findRunnerById(String id) {
    ProgramRunner[] registeredRunners = getRegisteredRunners();
    for (ProgramRunner registeredRunner : registeredRunners) {
      if (Comparing.equal(id, registeredRunner.getInfo().getId())) {
        return registeredRunner;
      }
    }
    return null;
  }

}
