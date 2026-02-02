// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.Executor;
import com.intellij.execution.RunContentDescriptorId;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.function.Predicate;

@ApiStatus.Internal
public interface RunDashboardUiManager {
  static @NotNull RunDashboardUiManager getInstance(@NotNull Project project) {
    return project.getService(RunDashboardUiManager.class);
  }

  static @Nullable RunDashboardUiManager getInstanceIfCreated(@NotNull Project project) {
    return project.getServiceIfCreated(RunDashboardUiManager.class);
  }

  @NotNull
  ContentManager getDashboardContentManager();

  @NotNull
  String getToolWindowId();

  @NotNull
  Icon getToolWindowIcon();

  @NotNull
  Predicate<Content> getReuseCondition();

  void setSelectedContent(@NotNull Content content);

  void removeFromSelection(@NotNull Content content);

  void contentReused(@NotNull Content content, @NotNull RunContentDescriptor oldDescriptor);

  //todo: split temporary method to disable run executor in Services tool window
  boolean isSupported(@NotNull Executor executor);

  void navigateToServiceOnRun(@NotNull RunContentDescriptorId descriptorId, Boolean focus);
}
