// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Project component that keeps track of {@link RunConfigurationProducer} implementations that should be ignored for a given project. All
 * subclasses of classes specified here will be ignored when looking for configuration producers.
 */
@Service(Service.Level.PROJECT)
@State(name = "RunConfigurationProducerService", storages = @Storage("runConfigurations.xml"))
@ApiStatus.Internal
public final class RunConfigurationProducerService implements PersistentStateComponent<RunConfigurationProducerService.State> {
  private State myState = new State();

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static final class State {
    public final Set<String> ignoredProducers = new HashSet<>();
  }

  public static @NotNull RunConfigurationProducerService getInstance(@NotNull Project project) {
    return project.getService(RunConfigurationProducerService.class);
  }

  public void addIgnoredProducer(@NotNull Class<? extends RunConfigurationProducer<?>> ignoredProducer) {
    myState.ignoredProducers.add(ignoredProducer.getName());
  }

  public boolean isIgnored(@NotNull RunConfigurationProducer<?> producer) {
    return myState.ignoredProducers.contains(producer.getClass().getName());
  }
}
