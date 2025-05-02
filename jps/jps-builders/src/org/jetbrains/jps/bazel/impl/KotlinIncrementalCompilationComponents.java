// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents;
import org.jetbrains.kotlin.modules.TargetId;


public class KotlinIncrementalCompilationComponents implements IncrementalCompilationComponents {
  private final String moduleName;
  private final IncrementalCache cache;

  public KotlinIncrementalCompilationComponents(String moduleName, IncrementalCache cache) {
    this.moduleName = moduleName;
    this.cache = cache;
  }

  @Override
  public @NotNull IncrementalCache getIncrementalCache(@NotNull TargetId targetId) {
    if (!targetId.getName().equals(moduleName)) throw new RuntimeException("Incremental cache for target " + moduleName + " not found");
    return cache;
  }
}