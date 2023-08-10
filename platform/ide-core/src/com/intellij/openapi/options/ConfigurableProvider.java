// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;

/**
 * Register implementation of this class as {@code projectConfigurable} or {@code applicationConfigurable} extension to provide items for
 * "Project Settings" and "IDE Settings" groups correspondingly in the "Settings" dialog
 *
 * @see Configurable
 * @see Configurable.WithEpDependencies
 */
public abstract class ConfigurableProvider {

  @Nullable
  public abstract Configurable createConfigurable();

  /**
   * Defines whether this provider creates a configurable or not.
   * Note that the {@code createConfigurable} method will be called
   * if this method returns {@code true}.
   *
   * @return {@code true} if this provider creates configurable,
   *         {@code false} otherwise
   */
  public boolean canCreateConfigurable() {
    return true;
  }
}
