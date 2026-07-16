// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.OverrideOnly
public interface BuildViewSettingsProvider {

  @NotNull BuildViewSettingsProvider EMPTY = new BuildViewSettingsProvider() {
  };

  default boolean isExecutionViewHidden() {
    return false;
  }

  /**
   * There are several ways to display the build output:
   * - a single build console view
   * - a multiple build console view
   * <p>
   * The multiple build console view is considered as deprecated and scheduled for removal later.
   * <p>
   * By default, the method return `false` for the compatibility purpose.
   * The expected method value is `true`.
   */
  default boolean isSingleBuildConsoleView() {
    return false;
  }
}
