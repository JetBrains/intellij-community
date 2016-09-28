/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the list of run/debug configurations in a project.
 *
 * @author anna
 * @see RunnerRegistry
 * @see ExecutionManager
 */
public abstract class RunManager {
  public static final String UNNAMED = "Unnamed";

  public static RunManager getInstance(final Project project) {
    return project.getComponent(RunManager.class);
  }

  /**
   * Returns the list of all registered configuration types.
   *
   * @return all registered configuration types.
   */
  @NotNull
  public abstract ConfigurationType[] getConfigurationFactories();

  /**
   * Returns the list of all configurations of a specified type.
   *
   * @param type a run configuration type.
   * @return all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  @NotNull
  @Deprecated
  public abstract RunConfiguration[] getConfigurations(@NotNull ConfigurationType type);

  /**
   * Returns the list of all configurations of a specified type.
   *
   * @param type a run configuration type.
   * @return all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  @NotNull
  public abstract List<RunConfiguration> getConfigurationsList(@NotNull ConfigurationType type);

  /**
   * Returns the list of {@link RunnerAndConfigurationSettings} for all configurations of a specified type.
   *
   * @param type a run configuration type.
   * @return settings for all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  @NotNull
  @Deprecated
  public abstract RunnerAndConfigurationSettings[] getConfigurationSettings(@NotNull ConfigurationType type);

  /**
   * Returns the list of {@link RunnerAndConfigurationSettings} for all configurations of a specified type.
   *
   * @param type a run configuration type.
   * @return settings for all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  @NotNull
  public abstract List<RunnerAndConfigurationSettings> getConfigurationSettingsList(@NotNull ConfigurationType type);

  /**
   * Returns the list of all run configurations.
   *
   * @return the list of all run configurations.
   */
  @NotNull
  @Deprecated
  public abstract RunConfiguration[] getAllConfigurations();

  /**
   * Returns the list of all run configurations.
   *
   * @return the list of all run configurations.
   */
  @NotNull
  public abstract List<RunConfiguration> getAllConfigurationsList();

  /**
   * Returns the list of all run configurations settings.
   *
   * @return the list of all run configurations settings.
   */
  @NotNull
  public abstract List<RunnerAndConfigurationSettings> getAllSettings();

  /**
   * Returns the list of all temporary run configurations.
   *
   * @return the list of all temporary run configurations.
   * @see RunnerAndConfigurationSettings#isTemporary()
   */
  @NotNull
  @Deprecated
  public abstract RunConfiguration[] getTempConfigurations();

  /**
   * Returns the list of all temporary run configurations settings.
   *
   * @return the list of all temporary run configurations settings.
   * @see RunnerAndConfigurationSettings#isTemporary()
   */
  @NotNull
  public abstract List<RunnerAndConfigurationSettings> getTempConfigurationsList();

  /**
   * Checks if the specified run configuration is temporary and will be deleted when the temporary configurations limit is exceeded.
   *
   * @return true if the configuration is temporary, false otherwise.
   * @see RunnerAndConfigurationSettings#isTemporary()
   */
  @Deprecated
  public abstract boolean isTemporary(@NotNull RunConfiguration configuration);

  /**
   * Saves the specified temporary run configuration and makes it a permanent one.
   *
   * @param configuration the temporary run configuration to save.
   */
  @Deprecated
  public abstract void makeStable(@NotNull RunConfiguration configuration);

  /**
   * Saves the specified temporary run settings and makes it a permanent one.
   *
   * @param settings the temporary settings to save.
   */
  public abstract void makeStable(@NotNull RunnerAndConfigurationSettings settings);

  /**
   * Returns the selected item in the run/debug configurations combobox.
   *
   * @return the selected configuration, or null if no configuration is defined or selected.
   */
  @Nullable
  public abstract RunnerAndConfigurationSettings getSelectedConfiguration();

  /**
   * Selects a configuration in the run/debug configurations combobox.
   *
   * @param configuration the configuration to select, or null if nothing should be selected.
   */
  public abstract void setSelectedConfiguration(@Nullable RunnerAndConfigurationSettings configuration);

  /**
   * Creates a configuration of the specified type with the specified name. Note that you need to call
   * {@link #addConfiguration(RunnerAndConfigurationSettings, boolean)} if you want the configuration to be persisted in the project.
   *
   * @param name the name of the configuration to create (should be unique and not equal to any other existing configuration)
   * @param type the type of the configuration to create.
   * @return the configuration settings object.
   * @see RunManager#suggestUniqueName(String, Collection)
   */
  @NotNull
  public abstract RunnerAndConfigurationSettings createRunConfiguration(@NotNull String name, @NotNull ConfigurationFactory type);

  /**
   * Creates a configuration settings object based on a specified {@link RunConfiguration}. Note that you need to call
   * {@link #addConfiguration(RunnerAndConfigurationSettings, boolean)} if you want the configuration to be persisted in the project.
   *
   * @param runConfiguration the run configuration
   * @param factory the factory instance.
   * @return the configuration settings object.
   */
  @NotNull
  public abstract RunnerAndConfigurationSettings createConfiguration(@NotNull RunConfiguration runConfiguration, @NotNull ConfigurationFactory factory);

  /**
   * Returns the template settings for the specified configuration type.
   *
   * @param factory the configuration factory.
   * @return the template settings.
   */
  @NotNull
  public abstract RunnerAndConfigurationSettings getConfigurationTemplate(ConfigurationFactory factory);

  /**
   * Adds the specified run configuration to the list of run configurations stored in the project.
   *
   * @param settings the run configuration settings.
   * @param isShared true if the configuration is marked as shared (stored in the versioned part of the project files), false if it's local
   *                 (stored in the workspace file).
   */
  public abstract void addConfiguration(final RunnerAndConfigurationSettings settings, final boolean isShared);

  /**
   * Marks the specified run configuration as recently used (the temporary run configurations are deleted in LRU order).
   *
   * @param profile the run configuration to mark as recently used.
   */
  public abstract void refreshUsagesList(RunProfile profile);

  @NotNull
  public static String suggestUniqueName(@NotNull String str, @NotNull Collection<String> currentNames) {
    if (!currentNames.contains(str)) return str;

    final Matcher matcher = Pattern.compile("(.*?)\\s*\\(\\d+\\)").matcher(str);
    final String originalName = (matcher.matches()) ? matcher.group(1) : str;
    int i = 1;
    while (true) {
      final String newName = String.format("%s (%d)", originalName, i);
      if (!currentNames.contains(newName)) return newName;
      i++;
    }
  }

  public String suggestUniqueName(@Nullable String name, @Nullable ConfigurationType type) {
    List<RunnerAndConfigurationSettings> settingsList = type != null ? getConfigurationSettingsList(type) : getAllSettings();
    List<String> names = ContainerUtil.map(settingsList, settings -> settings.getName());
    return suggestUniqueName(StringUtil.notNullize(name, UNNAMED), names);
  }

  /**
   * Sets unique name if existing one is not 'unique'
   * If settings type is not null (for example settings may be provided by plugin that is unavailable after IDE restart, so type would be suddenly null)
   * name will be chosen unique for certain type otherwise name will be unique among all configurations
   * @return <code>true</code> if name was changed
   */
  public boolean setUniqueNameIfNeed(@NotNull RunnerAndConfigurationSettings settings) {
    String oldName = settings.getName();
    settings.setName(suggestUniqueName(StringUtil.notNullize(oldName, UNNAMED), settings.getType()));
    return !Comparing.equal(oldName, settings.getName());
  }

  /**
   * Sets unique name if existing one is not 'unique' for corresponding configuration type
   * @return <code>true</code> if name was changed
   */
  public boolean setUniqueNameIfNeed(@NotNull RunConfiguration configuration) {
    String oldName = configuration.getName();
    configuration.setName(suggestUniqueName(StringUtil.notNullize(oldName, UNNAMED), configuration.getType()));
    return !Comparing.equal(oldName, configuration.getName());
  }
}
