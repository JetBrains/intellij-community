// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Interface for run configurations which can be managed by a user and displayed in the UI.
 *
 * @see com.intellij.execution.RunManager
 * @see RunConfigurationBase
 *
 * If debugger is provided by plugin, RunConfiguration should also implement RunConfigurationWithSuppressedDefaultDebugAction
 * Otherwise (in case of disabled plugin) debug action may be enabled in UI but with no reaction
 * @see RunConfigurationWithSuppressedDefaultDebugAction
 *
 * @see RefactoringListenerProvider
 */
public interface RunConfiguration extends RunProfile, Cloneable {
  DataKey<RunConfiguration> DATA_KEY = DataKey.create("runtimeConfiguration");

  /**
   * Returns the type of the run configuration.
   */
  @NotNull
  default ConfigurationType getType() {
    ConfigurationFactory factory = getFactory();
    return factory == null ? UnknownConfigurationType.INSTANCE : factory.getType();
  }

  /**
   * Returns the factory that has created the run configuration.
   */
  @Nullable
  ConfigurationFactory getFactory();

  /**
   * Sets the name of the configuration.
   *
   * @param name the new name of the configuration.
   */
  void setName(String name);

  /**
   * Returns the UI control for editing the run configuration settings. If additional control over validation is required, the object
   * returned from this method may also implement {@link com.intellij.execution.impl.CheckableRunConfigurationEditor}. The returned object
   * can also implement {@link com.intellij.openapi.options.SettingsEditorGroup} if the settings it provides need to be displayed in
   * multiple tabs.
   *
   * @return the settings editor component.
   */
  @NotNull
  SettingsEditor<? extends RunConfiguration> getConfigurationEditor();

  /**
   * Returns the project in which the run configuration exists.
   */
  Project getProject();

  /**
   * Creates a block of settings for a specific {@link ProgramRunner}. Can return null if the configuration has no settings specific
   * to a program runner.
   *
   * @param provider source of assorted information about the configuration being edited.
   * @return the per-runner settings.
   */
  @Nullable
  default ConfigurationPerRunnerSettings createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  /**
   * Creates a UI control for editing the settings for a specific {@link ProgramRunner}. Can return null if the configuration has no
   * settings specific to a program runner.
   *
   * @param runner the runner the settings for which need to be edited.
   * @return the editor for the per-runner settings.
   */
  @Nullable
  default SettingsEditor<ConfigurationPerRunnerSettings> getRunnerSettingsEditor(ProgramRunner runner) {
    return null;
  }

  /**
   * Clones the run configuration.
   *
   * @return a clone of this run configuration.
   */
  RunConfiguration clone();

  /**
   * Returns the unique identifier of the run configuration. The identifier does not need to be persisted between the sessions.
   *
   * @return the unique ID of the configuration.
   */
  @Deprecated
  default int getUniqueID() {
    return System.identityHashCode(this);
  }

  /**
   * Returns the unique identifier of the run configuration. Return null if not applicable.
   * Used only for non-managed RC type.
   */
  @Nullable
  default String getId() {
    return null;
  }

  @NotNull
  @Transient
  default String getPresentableType() {
    if (PlatformUtils.isPhpStorm()) {
      return " (" + StringUtil.first(getType().getDisplayName(), 10, true) + ")";
    }
    return "";
  }

  /**
   * If this method returns true, disabled executor buttons (e.g. Run) will be hidden when this configuration is selected.
   * Note that this will lead to UI flickering when switching between this configuration and others for which this property
   * is false, so you should avoid overriding this method unless you're really sure of what you're doing.
   */
  default boolean hideDisabledExecutorButtons() {
    return false;
  }

  /**
   * Checks whether the run configuration settings are valid.
   * Note that this check may be invoked on every change (i.e. after each character typed in an input field).
   *
   * @throws RuntimeConfigurationException if the configuration settings contain a non-fatal problem which the user should be warned about
   *                                       but the execution should still be allowed.
   * @throws RuntimeConfigurationError     if the configuration settings contain a fatal problem which makes it impossible
   *                                       to execute the run configuration.
   */
  default void checkConfiguration() throws RuntimeConfigurationException {
  }

  default void readExternal(@NotNull Element element) {
  }

  default void writeExternal(Element element) {
  }

  @NotNull
  default List<BeforeRunTask> getBeforeRunTasks() {
    return Collections.emptyList();
  }

  default void setBeforeRunTasks(@NotNull List<BeforeRunTask> value) {
  }
}
