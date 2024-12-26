// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * Describes a specific way of executing any possible run configuration. The three default executors provided by the IntelliJ Platform
 * by default are Run, Debug and Run with Coverage. Each executor gets its
 * own toolbar button, which starts the selected run configuration using this executor, and its own context menu item for starting
 * a configuration using this executor.
 *
 * @see ExecutorRegistry
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/execution.html">Execution (IntelliJ Platform Docs)</a>
 */
public abstract class Executor {
  public static final ExtensionPointName<Executor> EXECUTOR_EXTENSION_NAME = new ExtensionPointName<>("com.intellij.executor");

  /**
   * @return the ID of the toolwindow (usually {@link com.intellij.openapi.wm.ToolWindowId#RUN} or
   * {@link com.intellij.openapi.wm.ToolWindowId#DEBUG}) in which the run tabs created by this executor will be displayed
   */
  public abstract @NotNull String getToolWindowId();

  public abstract @NotNull Icon getToolWindowIcon();

  /**
   * @return the 16x16 icon for the toolbar button corresponding to the executor
   */
  public abstract @NotNull Icon getIcon();

  public @NotNull Icon getRerunIcon() {
    return getIcon();
  }

  /**
   * @return the 16x16 icon for the disabled toolbar button corresponding to the executor
   */
  public abstract Icon getDisabledIcon();

  /**
   * @return the action description (text displayed in the status bar) for the toolbar button corresponding to the executor
   */
  public abstract @NlsActions.ActionDescription String getDescription();

  public abstract @NotNull @NlsActions.ActionText String getActionName();

  /**
   * @return tool window title text that will be applied to a tool window when it's customized.
   */
  public @NotNull @NlsContexts.TabTitle String getToolWindowTitle() {
    return getActionName();
  }

  /**
   * @return the unique ID of the executor
   */
  public abstract @NotNull @NonNls String getId();

  /**
   * @return text of the action in {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format
   */
  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getStartActionText();

  public abstract @NonNls String getContextActionId();

  public abstract @NonNls String getHelpId();

  /**
   * @return text of the action specialized for given configuration name
   * in {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format.
   * 
   * @implNote The default implementation incorrectly assumes that the configuration name
   * is concatenated to the end of the action text. For internationalization purposes, 
   * it's highly desired to override this method and provide a separate template.
   * E.g., see {@link DefaultRunExecutor#getStartActionText(String)}
   */
  public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getStartActionText(@NlsSafe @NotNull String configurationName) {
    String configName = StringUtil.isEmpty(configurationName) ? "" : " '" + shortenNameIfNeeded(configurationName) + "'";
    return TextWithMnemonic.parse(getStartActionText()).append(configName).toString();
  }

  /**
   * Return false to suppress action visibility for given project.
   */
  public boolean isApplicable(@NotNull Project project) {
    return true;
  }

  /**
   * @return whether the executor can be run on targets or not.
   */
  @ApiStatus.Experimental
  public boolean isSupportedOnTarget() {
    return false;
  }

  /**
   * Too long names don't fit into UI controls and have to be trimmed
   */
  @Contract(pure = true)
  public static String shortenNameIfNeeded(@NotNull String name) {
    return StringUtil.trimMiddle(name, Registry.intValue("run.configuration.max.name.length", 80));
  }
}
