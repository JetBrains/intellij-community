/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * Named component which provides a configuration user interface. Components which
 * implement {@link com.intellij.openapi.components.ApplicationComponent} and
 * this interface are shown as items in the "IDE Settings" group of the main
 * IDEA settings dialog.
 * Components which implement {@link com.intellij.openapi.components.ProjectComponent} and
 * this interface are shown in the "Project Settings" group of that dialog.
 */
public interface Configurable extends UnnamedConfigurable {
  /**
   * Returns the user-visible name of the settings component.
   *
   * @return the visible name of the component.
   */
  @Nls
  String getDisplayName();

  /**
   * Returns the icon representing the settings component. Components
   * shown in the IDEA settings dialog have 32x32 icons.
   *
   * @return the icon for the component.
   */
  Icon getIcon();

  /**
   * Returns the topic in the help file which is shown when help for the configurable
   * is requested.
   *
   * @return the help topic, or null if no help is available.
   */
  @Nullable
  @NonNls String getHelpTopic();
}
