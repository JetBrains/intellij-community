// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface RunManagerListener extends EventListener {
  Topic<RunManagerListener> TOPIC = new Topic<>("RunManager", RunManagerListener.class);

  default void runConfigurationSelected(@Nullable RunnerAndConfigurationSettings settings) {
    //noinspection deprecation
    runConfigurationSelected();
  }

  /**
   * @deprecated implement {@link #runConfigurationSelected(RunnerAndConfigurationSettings)}
   */
  @Deprecated
  default void runConfigurationSelected() {
  }

  default void beforeRunTasksChanged() {
  }

  default void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
  }

  default void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
  }

  default void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings, @Nullable String existingId) {
    runConfigurationChanged(settings);
  }

  default void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
  }

  default void beginUpdate() {
  }

  default void endUpdate() {
  }

  /**
   * Called also in case when RunManager doesn't have state.
   */
  default void stateLoaded(@NotNull RunManager runManager, boolean isFirstLoadState) {
  }
}
