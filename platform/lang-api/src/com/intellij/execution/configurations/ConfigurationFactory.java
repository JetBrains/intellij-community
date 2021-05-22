// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.DeprecatedMethodException;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * Factory for run configuration instances.
 * @see ConfigurationType#getConfigurationFactories()
 */
public abstract class ConfigurationFactory {
  public static final ConfigurationFactory[] EMPTY_ARRAY = new ConfigurationFactory[0];

  private final ConfigurationType myType;

  protected ConfigurationFactory(@NotNull ConfigurationType type) {
    myType = type;
  }

  ConfigurationFactory() {
    myType = null;
  }

  /**
   * Creates a new run configuration with the specified name by cloning the specified template.
   *
   * @param name the name for the new run configuration.
   * @param template the template from which the run configuration is copied
   * @return the new run configuration.
   */
  public @NotNull RunConfiguration createConfiguration(@NlsSafe @Nullable String name, @NotNull RunConfiguration template) {
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
  public abstract @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project);

  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project, @NotNull RunManager runManager) {
    return createTemplateConfiguration(project);
  }

  /**
   * Returns the id of the run configuration that is used for serialization. For compatibility reason the default implementation calls
   * the method {@link #getName()} and this may cause problems if {@link #getName} returns localized value. So the default implementation
   * <strong>must be overridden</strong> in all inheritors. In existing implementations you need to use the same value which is returned
   * by {@link #getName()} for compatibility but store it directly in the code instead of taking from a message bundle. For new configurations
   * you may use any unique ID; if a new {@link ConfigurationType} has a single {@link ConfigurationFactory}, use {@link SimpleConfigurationType} instead.
   */
  public @NotNull @NonNls String getId() {
    DeprecatedMethodException.reportDefaultImplementation(getClass(), "getId",
      "The default implementation delegates to 'getName' which may be localized but return value of this method must not depend on current localization.");
    return getName();
  }

  /**
   * The name of the run configuration variant created by this factory.
   */
  public @NotNull @Nls String getName() {
    // null only if SimpleConfigurationType (but method overridden)
    //noinspection ConstantConditions
    return myType.getDisplayName();
  }

  /** @deprecated Use {@link com.intellij.icons.AllIcons.General#Add} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public Icon getAddIcon() {
    return IconUtil.getAddIcon();
  }

  public Icon getIcon(final @NotNull RunConfiguration configuration) {
    return getIcon();
  }

  public Icon getIcon() {
    // null only if SimpleConfigurationType (but method overridden)
    //noinspection ConstantConditions
    return myType.getIcon();
  }

  public @NotNull ConfigurationType getType() {
    // null only if SimpleConfigurationType (but method overridden)
    //noinspection ConstantConditions
    return myType;
  }

  /**
   * In this method you can configure defaults for the task, which are preferable to be used for your particular configuration type
   */
  @SuppressWarnings("rawtypes")
  public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) { }

  /** @deprecated Use {@link RunConfigurationSingletonPolicy} */
  @Deprecated
  public boolean isConfigurationSingletonByDefault() {
    return getSingletonPolicy() != RunConfigurationSingletonPolicy.MULTIPLE_INSTANCE;
  }

  /** @deprecated Use {@link RunConfigurationSingletonPolicy} */
  @Deprecated
  public boolean canConfigurationBeSingleton() {
    return getSingletonPolicy() != RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY;
  }

  public @NotNull RunConfigurationSingletonPolicy getSingletonPolicy() {
    return RunConfigurationSingletonPolicy.SINGLE_INSTANCE;
  }

  public boolean isEditableInDumbMode() {
    return false;
  }

  public @Nullable Class<? extends BaseState> getOptionsClass() {
    return null;
  }
}
