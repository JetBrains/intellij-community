// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;

/**
 * Register implementations of this class as {@code com.intellij.projectConfigurable} or {@code com.intellij.applicationConfigurable}
 * extension to provide items for "Settings" dialog.
 *
 * @see Configurable
 * @see Configurable.WithEpDependencies
 */
public abstract class ConfigurableProvider {

  public abstract @Nullable Configurable createConfigurable();

  /**
   * Defines whether this provider creates a configurable or not.
   * Note that the {@code createConfigurable} method will be called
   * if this method returns {@code true}.
   *
   * @return {@code true} if this provider creates configurable,
   * {@code false} otherwise
   */
  public boolean canCreateConfigurable() {
    return true;
  }
}
