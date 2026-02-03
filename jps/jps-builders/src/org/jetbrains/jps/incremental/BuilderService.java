// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;

import java.util.List;

/**
 * The main entry point for the external build system plugins. Implementations of this class are registered as Java services, by
 * creating a file META-INF/services/org.jetbrains.jps.incremental.BuilderService containing the qualified name of your implementation
 * class.
 */
public abstract class BuilderService {
  /**
   * Returns the list of build target types contributed by this plugin. If it only participates in the compilation
   * of regular Java modules, you don't need to return anything here.
   */
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    return List.of();
  }

  /**
   * Returns the list of Java module builder extensions contributed by this plugin.
   */
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return List.of();
  }

  /**
   * Returns the list of non-module target builders contributed by this plugin.
   */
  public @NotNull List<? extends TargetBuilder<?,?>> createBuilders() {
    return List.of();
  }
}
