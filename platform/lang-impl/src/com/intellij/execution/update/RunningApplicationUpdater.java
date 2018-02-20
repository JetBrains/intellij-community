// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.update;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Instance of {@link RunningApplicationUpdater} may be provided to {@link UpdateRunningApplicationAction}
 * by {@link RunningApplicationUpdaterProvider}.
 * {@link UpdateRunningApplicationAction} will be available for user if at least one updater is provided.
 * {@link #performUpdate()} will be called on performing the action.
 * Popup with available updaters will be shown at first if there more then one available updater.
 */
public interface RunningApplicationUpdater {
  String getDescription();

  String getShortName();

  @Nullable
  Icon getIcon();

  default boolean isEnabled() {
    return true;
  }

  void performUpdate();
}
