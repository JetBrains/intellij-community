// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public interface ServiceViewManager {
  static ServiceViewManager getInstance(Project project) {
    return project.getService(ServiceViewManager.class);
  }

  @NotNull
  Promise<Void> select(@NotNull Object service, @NotNull Class<?> rootContributorClass, boolean activate, boolean focus);

  @NotNull
  Promise<Void> expand(@NotNull Object service, @NotNull Class<?> rootContributorClass);

  @NotNull
  Promise<Void> extract(@NotNull Object service, @NotNull Class<?> rootContributorClass);

  @Nullable
  String getToolWindowId(@NotNull Class<?> rootContributorClass);
}
