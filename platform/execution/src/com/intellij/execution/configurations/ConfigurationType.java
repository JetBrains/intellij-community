// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getDisplayName();

  /**
   * Returns the description of the configuration type. You may return the same text as the display name of the configuration type.
   *
   * @return the description of the configuration type.
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getConfigurationTypeDescription();

  /**
   * Returns the 16x16 icon used to represent the configuration type.
   *
   * @return the icon
   */
  Icon getIcon();

  /**
   * The ID of the configuration type. Should be camel-cased without dashes, underscores, spaces and quotation marks.
   * The ID is used to store run configuration settings in a project or workspace file and
   * must not change between plugin versions.
   */
  @NotNull @NonNls
  String getId();

  /**
   * The name of the run configuration group in a configuration file. The same rules as for id. Useful when id cannot be changed.
   */
  @NotNull @NonNls
  default String getTag() {
    return getId();
  }

  /**
   * Returns the configuration factories used by this configuration type. Normally each configuration type provides just a single factory.
   * You can return multiple factories if your configurations can be created in multiple variants (for example, local and remote for an
   * application server).
   *
   * @return the run configuration factories.
   */
  ConfigurationFactory[] getConfigurationFactories();

  /**
   * Returns the topic in the help file or in Web Help which is shown when help for configurations of this type is requested.
   *
   * @return the help topic, or {@code null} if no help is available
   */
  @NonNls
  @Nullable
  default String getHelpTopic() {
    return null;
  }

  /**
   * Is configuration fully managed by RunManager.
   */
  default boolean isManaged() {
    return true;
  }
}