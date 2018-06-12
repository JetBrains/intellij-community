// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The type of a run configuration.
 *
 * @see ConfigurationTypeBase
 */
public interface ConfigurationType extends PossiblyDumbAware {

  ExtensionPointName<ConfigurationType> CONFIGURATION_TYPE_EP = ExtensionPointName.create("com.intellij.configurationType");

  /**
   * Returns the display name of the configuration type. This is used, for example, to represent the configuration type in the run
   * configurations tree, and also as the name of the action used to create the configuration.
   *
   * @return the display name of the configuration type.
   */
  @Nls
  String getDisplayName();

  /**
   * Returns the description of the configuration type. You may return the same text as the display name of the configuration type.
   *
   * @return the description of the configuration type.
   */
  @Nls
  String getConfigurationTypeDescription();

  /**
   * Returns the 16x16 icon used to represent the configuration type.
   *
   * @return the icon
   */
  Icon getIcon();

  /**
   * Returns the ID of the configuration type. The ID is used to store run configuration settings in a project or workspace file and
   * must not change between plugin versions.
   *
   * @return the configuration type ID.
   */
  @NonNls @NotNull
  String getId();

  /**
   * Returns the configuration factories used by this configuration type. Normally each configuration type provides just a single factory.
   * You can return multiple factories if your configurations can be created in multiple variants (for example, local and remote for an
   * application server).
   *
   * @return the run configuration factories.
   */
  ConfigurationFactory[] getConfigurationFactories();

  /**
   * Is configuration fully managed by RunManager.
   */
  default boolean isManaged() {
    return true;
  }
}