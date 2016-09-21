/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.wm;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Performs lazy initialization of a toolwindow registered in plugin.xml.
 * Please implement {@link com.intellij.openapi.project.DumbAware} marker interface to indicate that the toolwindow content should be
 * available during indexing process.
 *
 * @author yole
 * @author Konstantin Bulenkov
 * @see ToolWindowEP
 */
public interface ToolWindowFactory {
  void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow);

  /**
   * Perform additional initialisation routine here
   * @param window Tool Window
   */
  default void init(ToolWindow window) {}

  /**
   * Tool Window saves its state on project close and restore on when project opens
   * In some cases, it is useful to postpone Tool Window activation until user explicitly activates it.
   * Example: Tool Window initialisation takes huge amount of time and makes project loading slower.
   * @return {@code true} if Tool Window should not be activated on start even if was opened previously.
   *         {@code false} otherwise.
   */
  default boolean isDoNotActivateOnStart() {return false;}
}
