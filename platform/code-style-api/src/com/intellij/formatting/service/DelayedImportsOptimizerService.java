// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface DelayedImportsOptimizerService {
  static DelayedImportsOptimizerService getInstance(Project project) {
    return project.getService(DelayedImportsOptimizerService.class);
  }

  boolean delayOptimizeImportsTask(@NotNull Task task);
}
