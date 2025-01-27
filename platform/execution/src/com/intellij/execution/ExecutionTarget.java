// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Execution targets allow running same run configuration on various targets such as devices, simulators etc.<br>
 * IDE can suggest possible targets for every configuration depending on its type or settings.<br>
 * <br>
 * When a run configuration is executed on a specific target, it becomes associated with this target and all the following actions (e.g. rerun, rerun failed tests)
 * are be performed on this target, even if another target is selected in the UI.<br>
 * <br>
 * Example:<br>
 *   AppCode suggests available iOS Devices and iOS Simulators for iOS run configuration,<br>
 *   while only showing OS X 32-bit/64-bit targets for OS X configurations.<br>
 * <br>
 * RunConfiguration can decide, if it can be run on a given target<br>
 * (see {@link com.intellij.execution.configurations.TargetAwareRunProfile#canRunOn(ExecutionTarget)})<br>
 * <br>
 * Targets are collected from {@link ExecutionTargetProvider}
 */
public abstract class ExecutionTarget {
  /**
   * Id is used to save selected target between restarts
   */
  public abstract @NotNull @NonNls String getId();

  public abstract @NotNull @Nls String getDisplayName();

  public @Nullable @Nls String getGroupName() {
    return null;
  }

  public abstract @Nullable Icon getIcon();

  public @Nullable @Nls String getDescription() { return null; }

  /**
   * Implementation-specific logic should decide whether to suggest this target for the given configuration.
   */
  public boolean canRun(@NotNull RunConfiguration configuration) {
    throw new AbstractMethodError();
  }

  /**
   * Checks if the target is ready to be selected as a default choice in the Run Configurations popup
   * @return true if the target is ready, false otherwise
   */
  public boolean isReady() {
    return true;
  }

  /**
   * Implementation-specific logic to determine if an external plugin is responsible for managing this target.
   * @return true if the target is externally managed, or false for the platform to manage
   */
  public boolean isExternallyManaged() {
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || (getClass().isInstance(obj) && getId().equals(((ExecutionTarget)obj).getId()));
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }

  @Override
  public String toString() {
    return getId();
  }
}
