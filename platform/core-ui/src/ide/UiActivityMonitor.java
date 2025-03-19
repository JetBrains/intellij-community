// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BusyObject;
import org.jetbrains.annotations.NotNull;

public abstract class UiActivityMonitor {
  public static UiActivityMonitor getInstance() {
    return ApplicationManager.getApplication().getService(UiActivityMonitor.class);
  }

  public abstract @NotNull BusyObject getBusy(@NotNull Project project, UiActivity @NotNull ... toWatch);

  public abstract @NotNull BusyObject getBusy(UiActivity @NotNull ... toWatch);

  public abstract void addActivity(@NotNull Project project, @NotNull UiActivity activity);

  public abstract void addActivity(@NotNull Project project, @NotNull UiActivity activity, @NotNull ModalityState effectiveModalityState);

  public abstract void addActivity(@NotNull UiActivity activity);

  public abstract void addActivity(@NotNull UiActivity activity, @NotNull ModalityState effectiveModalityState);

  public abstract void removeActivity(@NotNull Project project, @NotNull UiActivity activity);

  public abstract void removeActivity(@NotNull UiActivity activity);

  public abstract void clear();

  public abstract void setActive(boolean active);
}
