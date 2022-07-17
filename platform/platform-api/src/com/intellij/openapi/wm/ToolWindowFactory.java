// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Performs lazy initialization of a tool window registered in {@code plugin.xml}.
 * Please implement {@link com.intellij.openapi.project.DumbAware} marker interface to indicate that the tool window content should be
 * available during the indexing process.
 * <p/>
 * To localize tool window stripe title, add key {@code toolwindow.stripe.yourToolWindowId.replace(" ", "_")} to plugin's resource bundle.
 * <p/>
 * See <a href="https://www.jetbrains.org/intellij/sdk/docs/user_interface_components/tool_windows.html">Tool Windows</a> in SDK Docs.
 */
public interface ToolWindowFactory {
  default boolean isApplicable(@NotNull Project project) {
    return true;
  }

  void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow);

  /**
   * Perform additional initialization routine here.
   */
  default void init(@NotNull ToolWindow toolWindow) {}

  /**
   * Check if tool window (and its stripe button) should be visible after startup.
   *
   * @see ToolWindow#isAvailable()
   */
  default boolean shouldBeAvailable(@NotNull Project project) {
    return true;
  }

  /**
   * @deprecated Use {@link ToolWindowEP#isDoNotActivateOnStart}
   */
  @Deprecated
  default boolean isDoNotActivateOnStart() {
    return false;
  }

  /**
   * Return custom anchor or null to use anchor defined in Tool Window Registration or customized by user.
   */
  @ApiStatus.Internal
  default @Nullable ToolWindowAnchor getAnchor() {
    return null;
  }

  @ApiStatus.Internal
  default @Nullable Icon getIcon() {
    return null;
  }
}
