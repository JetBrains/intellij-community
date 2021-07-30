// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allows a plugin to extend a run configuration created by another plugin.
 *
 * @author traff
 */
public abstract class RunConfigurationExtensionBase<T extends RunConfigurationBase<?>> {
  /**
   * Returns the ID used to serialize the settings.
   *
   * @return the serialization ID (must be unique across all run configuration extensions).
   */
  @NotNull
  protected String getSerializationId() {
    return getClass().getCanonicalName();
  }

  /**
   * Loads the settings of this extension from the run configuration XML element. In memory, the settings can be placed into the
   * user data of the run configuration.
   *
   * @param runConfiguration the run configuration being deserialized.
   * @param element          the element with persisted settings.
   */
  protected void readExternal(@NotNull final T runConfiguration, @NotNull final Element element) {
  }

  /**
   * Saves the settings of this extension to the run configuration XML element.
   *
   * @param runConfiguration the run configuration being serialized.
   * @param element          the element into which the settings should be persisted,
   */
  protected void writeExternal(@NotNull T runConfiguration, @NotNull Element element) {
  }

  /**
   * Creates an editor for the settings of this extension. The editor is displayed as an additional tab of the run configuration options
   * in the Run/Debug Configurations dialog.
   *
   * @param configuration the configuration being edited.
   * @return the editor component, or null if this extension doesn't provide any UI for editing the settings.
   */
  @Nullable
  protected <P extends T> SettingsEditor<P> createEditor(@NotNull final P configuration) {
    return null;
  }

  protected <P extends T> List<SettingsEditorFragment<P, ?>> createFragments(@NotNull P configuration) {
    return null;
  }

  /**
   * Returns the title of the tab in which the settings editor is displayed.
   *
   * @return the editor tab title, or null if this extension doesn't provide any UI for editing the settings.
   */
  @Nullable
  @TabTitle
  protected String getEditorTitle() {
    return null;
  }

  /**
   * @param configuration Run configuration
   * @return True if extension in general applicable to given run configuration - just to attach settings tab, etc. But extension may be
   *         turned off in its settings. E.g. RCov in general available for given run configuration, but may be turned off.
   */
  public abstract boolean isApplicableFor(@NotNull final T configuration);

  /**
   *
   * @param applicableConfiguration Applicable run configuration
   * @return True if extension is turned on in configuration extension settings.
   *         E.g. RCov is turned on for given run configuration.
   */
  public abstract boolean isEnabledFor(@NotNull final T applicableConfiguration, @Nullable RunnerSettings runnerSettings);

  /**
   * Patches the command line of the process about to be started by the underlying run configuration.
   *
   * @param configuration  the underlying run configuration.
   * @param runnerSettings the runner-specific settings.
   * @param cmdLine        the command line of the process about to be started.
   * @param runnerId       the ID of the {@link com.intellij.execution.runners.ProgramRunner} used to start the process.
   * @throws ExecutionException if there was an error configuring the command line and the execution should be canceled.
   */
  protected abstract void patchCommandLine(@NotNull final T configuration,
                                           @Nullable RunnerSettings runnerSettings,
                                           @NotNull final GeneralCommandLine cmdLine,
                                           @NotNull final String runnerId) throws ExecutionException;

  /**
   * Patches the command line of the process about to be started by the underlying run configuration.
   *
   * @param configuration  the underlying run configuration.
   * @param runnerSettings the runner-specific settings.
   * @param cmdLine        the command line of the process about to be started.
   * @param runnerId       the ID of the {@link com.intellij.execution.runners.ProgramRunner} used to start the process.
   * @param executor       the executor which is using to run the configuration
   * @throws ExecutionException if there was an error configuring the command line and the execution should be canceled.
   */
  protected void patchCommandLine(@NotNull final T configuration,
                                           @Nullable RunnerSettings runnerSettings,
                                           @NotNull final GeneralCommandLine cmdLine,
                                           @NotNull final String runnerId,
                                           @NotNull final Executor executor) throws ExecutionException {
    patchCommandLine(configuration, runnerSettings, cmdLine, runnerId);
  }


  /**
   * Attaches the extension to a process that has been started.
   *
   * @param configuration  the underlying run configuration.
   * @param handler        the ProcessHandler for the running process.
   * @param runnerSettings the runner-specific settings.
   */
  protected void attachToProcess(@NotNull final T configuration,
                                 @NotNull final ProcessHandler handler,
                                 @Nullable RunnerSettings runnerSettings) {

  }

  /**
   * Validate extensions after general configuration validation passed
   *
   * @param isExecution   true if the configuration is about to be executed, false if the configuration settings are being edited.
   *
   */
  protected void validateConfiguration(@NotNull final T configuration, final boolean isExecution) throws Exception {
  }

  /**
   * Setup extension settings for a run configuration that has been created from context.
   *
   * @param configuration Configuration created from context.
   * @param location      the location from which the configuration was created.
   */
  protected void extendCreatedConfiguration(@NotNull final T configuration,
                                            @NotNull final Location location) {

  }

  protected void extendTemplateConfiguration(@NotNull final T configuration) {
  }
}
