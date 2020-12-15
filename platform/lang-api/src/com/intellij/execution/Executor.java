// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
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
  @NotNull
  public abstract String getToolWindowId();

  @NotNull
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
  @NlsActions.ActionDescription
  public abstract String getDescription();

  @NotNull
  @NlsActions.ActionText
  public abstract String getActionName();

  /**
   * Returns the unique ID of the executor.
   *
   * @return the ID of the executor.
   */
  @NotNull
  @NonNls
  public abstract String getId();

  /**
   * @return text of the action in {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  public abstract String getStartActionText();

  @NonNls
  public abstract String getContextActionId();

  @NonNls
  public abstract String getHelpId();

  /**
   * Returns the way to customize ExecutorAction (or ExecutorGroupActionGroup) created for this Executor by ExecutorRegistryImpl
   *
   * @return the way to customize {@link com.intellij.execution.ExecutorRegistryImpl.ExecutorAction}
   * (or {@link com.intellij.execution.ExecutorRegistryImpl.ExecutorGroupActionGroup}) created for this Executor,
   * that will be shown in {@link com.intellij.execution.ExecutorRegistryImpl#RUNNERS_GROUP} group on main toolbar
   */
  @Nullable
  public ActionWrapper runnerActionsGroupExecutorActionCustomizer() {
    return null;
  }

  @FunctionalInterface
  public interface ActionWrapper {
    @NotNull
    AnAction wrap(@NotNull AnAction original);
  }

  /**
   * @return text of the action specialized for given configuration name
   * in {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format.
   */
  @NotNull
  @NlsSafe
  public String getStartActionText(@NlsSafe @NotNull String configurationName) {
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
