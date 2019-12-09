// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Performs lazy initialization of a tool window registered in {@code plugin.xml}.
 * Please implement {@link com.intellij.openapi.project.DumbAware} marker interface to indicate that the tool window content should be
 * available during the indexing process.
 *
 * @author yole
 * @author Konstantin Bulenkov
 * @see ToolWindowEP
 */
public interface ToolWindowFactory {
  void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow);

  /**
   * Perform additional initialization routine here.
   */
  default void init(ToolWindow window) {}

  /**
   * Check if tool window (and its stripe button) should be visible after startup.
   *
   * @see ToolWindow#isAvailable()
   */
  default boolean shouldBeAvailable(@NotNull Project project) {
    return true;
  }

  /**
   * Tool window saves its state on project close and restore on when project opens.
   * In some cases, it is useful to postpone its activation until the user explicitly activates it.
   * Example: Tool Window initialization takes a huge amount of time and makes project loading slower.
   *
   * @return {@code true} if Tool Window should not be activated on start even if was opened previously.
   * {@code false} otherwise. Please note that active (visible and focused) tool window would be activated on start in any case.
   */
  default boolean isDoNotActivateOnStart() {
    return false;
  }
}
