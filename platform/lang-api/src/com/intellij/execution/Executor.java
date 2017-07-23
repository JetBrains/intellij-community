/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Describes a specific way of executing any possible run configuration. The three default executors provided by the IntelliJ Platform
 * by default are Run, Debug and Run with Coverage. Each executor gets its
 * own toolbar button, which starts the selected run configuration using this executor, and its own context menu item for starting
 * a configuration using this executor.
 *
 * @author spleaner
 */
public abstract class Executor {
  public static final ExtensionPointName<Executor> EXECUTOR_EXTENSION_NAME = ExtensionPointName.create("com.intellij.executor");

  /**
   * Returns the ID of the toolwindow in which the run tabs created by this executor will be displayed.
   *
   * @return the ID of the toolwindow (usually {@link com.intellij.openapi.wm.ToolWindowId#RUN} or
   * {@link com.intellij.openapi.wm.ToolWindowId#DEBUG}).
   */
  public abstract String getToolWindowId();

  public abstract Icon getToolWindowIcon();

  /**
   * Returns the 16x16 icon for the toolbar button corresponding to the executor.
   *
   * @return the icon.
   */
  @NotNull
  public abstract Icon getIcon();

  /**
   * Returns the 16x16 icon for the disabled toolbar button corresponding to the executor.
   *
   * @return the icon for the disabled button.
   */
  public abstract Icon getDisabledIcon();

  /**
   * Returns the action description (text displayed in the status bar) for the toolbar button corresponding to the executor.
   *
   * @return the executor action description.
   */
  public abstract String getDescription();

  @NotNull
  public abstract String getActionName();

  /**
   * Returns the unique ID of the executor.
   *
   * @return the ID of the executor.
   */
  @NotNull
  @NonNls
  public abstract String getId();

  @NotNull
  public abstract String getStartActionText();

  @NonNls
  public abstract String getContextActionId();

  @NonNls
  public abstract String getHelpId();

  public String getStartActionText(String configurationName) {
    return getStartActionText() + (StringUtil.isEmpty(configurationName) ? "" : " '" + shortenNameIfNeed(configurationName) + "'");
  }

  /**
   * Too long names don't fit into UI controls and have to be trimmed
   */
  public static String shortenNameIfNeed(@NotNull String name) {
    return StringUtil.first(name, Registry.intValue("run.configuration.max.name.length", 40), true);
  }
}
