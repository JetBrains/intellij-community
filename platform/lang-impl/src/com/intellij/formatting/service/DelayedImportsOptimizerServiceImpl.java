// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;

public class DelayedImportsOptimizerServiceImpl implements DelayedImportsOptimizerService {
  @Override
  public boolean delayOptimizeImportsTask(@NotNull Task task) {
    return false;
  }
}
