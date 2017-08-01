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
package com.intellij.execution.configurations;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Factory for run configuration instances.
 *
 * @see com.intellij.execution.configurations.ConfigurationType#getConfigurationFactories()
 * @author dyoma
 */
public abstract class ConfigurationFactory {
  public static final Icon ADD_ICON = IconUtil.getAddIcon();

  private final ConfigurationType myType;

  protected ConfigurationFactory(@NotNull final ConfigurationType type) {
    myType = type;
  }

  /**
   * Creates a new run configuration with the specified name by cloning the specified template.
   *
   * @param name the name for the new run configuration.
   * @param template the template from which the run configuration is copied
   * @return the new run configuration.
   */
  public RunConfiguration createConfiguration(String name, RunConfiguration template) {
    RunConfiguration newConfiguration = template.clone();
    newConfiguration.setName(name);
    return newConfiguration;
  }

  /**
   * Override this method and return {@code false} to hide the configuration from 'New' popup in 'Edit Configurations' dialog. It will be
   * still possible to create this configuration by clicking on '42 more items' in the 'New' popup.
   *
   * @return {@code true} if it makes sense to create configurations of this type in {@code project}
   */
  public boolean isApplicable(@NotNull Project project) {
    return true;
  }

  /**
   * Creates a new template run configuration within the context of the specified project.
   *
   * @param project the project in which the run configuration will be used
   * @return the run configuration instance.
   */
  @NotNull
  public abstract RunConfiguration createTemplateConfiguration(@NotNull Project project);

  @NotNull
  public RunConfiguration createTemplateConfiguration(@NotNull Project project, @NotNull RunManager runManager) {
    return createTemplateConfiguration(project);
  }

  /**
   * Returns the id of the run configuration that is used for serialization.
   * For compatibility reason the default implementation calls
   * the method <code>getName</code> instead of <code>myType.getId()</code>.
   *
   * New implementations need to call <code>myType.getId()</code> by default.
   *
   * @return the id of the run configuration that is used for serialization
   */
  @NonNls
  public String getId() {
    return getName();
  }

  /**
   * Returns the name of the run configuration variant created by this factory.
   *
   * @return the name of the run configuration variant created by this factory
   */
  @Nls
  public String getName() {
    return myType.getDisplayName();
  }

  public Icon getAddIcon() {
    return ADD_ICON;
  }

  public Icon getIcon(@NotNull final RunConfiguration configuration) {
    return getIcon();
  }

  public Icon getIcon() {
    return myType.getIcon();
  }

  @NotNull
  public ConfigurationType getType() {
    return myType;
  }

  /**
   * In this method you can configure defaults for the task, which are preferable to be used for your particular configuration type
   * @param providerID
   * @param task
   */
  public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
  }

  public boolean isConfigurationSingletonByDefault() {
    return false;
  }

  public boolean canConfigurationBeSingleton() {
    return true; // Configuration may be marked as singleton by default
  }
}
