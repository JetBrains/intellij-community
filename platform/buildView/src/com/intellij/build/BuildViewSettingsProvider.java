// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

@OverrideOnly
public interface BuildViewSettingsProvider {

  @NotNull BuildViewSettingsProvider EMPTY = new BuildViewSettingsProvider() {
  };

  default boolean isExecutionViewHidden() {
    return false;
  }

  /**
   * There are several ways to display the build output:
   * <ul>
   *   <li>a single build console view</li>
   *   <li>a multiple build console view</li>
   * </ul>
   * The multiple build console view is considered as deprecated and scheduled for removal later.
   * <p>
   * The method also defines the build view text source:
   * <ul>
   *   <li>{@code true}: {@link com.intellij.build.events.OutputBuildEvent}</li>
   *   <li>{@code false}: {@link com.intellij.execution.process.ProcessEvent}</li>
   * </ul>
   * <p>
   * By default, the method return `false` for the compatibility purpose.
   * The expected method value is `true`.
   */
  default boolean isSingleBuildConsoleView() {
    return false;
  }
}
