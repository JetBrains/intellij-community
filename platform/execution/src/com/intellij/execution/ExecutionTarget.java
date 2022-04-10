// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import org.jetbrains.annotations.*;

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
  @NotNull
  @NonNls
  public abstract String getId();

  @NotNull
  @Nls
  public abstract String getDisplayName();

  @Nullable
  public abstract Icon getIcon();

  /**
   * @deprecated implement {@link #canRun(RunConfiguration)} instead
   */
  @Deprecated(forRemoval = true)
  public boolean canRun(@NotNull RunnerAndConfigurationSettings configuration) {
    return canRun(configuration.getConfiguration());
  }

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
