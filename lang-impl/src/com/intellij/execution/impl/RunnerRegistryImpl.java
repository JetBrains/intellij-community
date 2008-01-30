package com.intellij.execution.impl;

import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


// TODO[spLeaner]: eliminate
public class RunnerRegistryImpl extends RunnerRegistry {
  private final List<ProgramRunner> myRunnersOrder = new ArrayList<ProgramRunner>();

  public RunnerRegistryImpl() {
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

  public synchronized void registerRunner(final ProgramRunner runner) {
    if (myRunnersOrder.contains(runner)) return;
    myRunnersOrder.add(runner);
  }

  public synchronized void unregisterRunner(final ProgramRunner runner) {
    myRunnersOrder.remove(runner);
  }

  public synchronized ProgramRunner[] getRegisteredRunners() {
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
