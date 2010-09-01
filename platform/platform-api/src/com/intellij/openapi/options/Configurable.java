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
package com.intellij.openapi.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Named component which provides a configuration user interface.
 *
 * <p><p>
 * Use {@code com.intellij.projectConfigurable} and {@code com.intellij.applicationConfigurable} extensions to provide items for
 * "Project Settings" and "IDE Settings" groups correspondingly in the "Settings" dialog. There are two ways to declare such extension:
 * <ul>
 * <li> an extension element with 'instance' attribute
 * <br>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;projectConfigurable instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;<br>
 * where 'class-name' implements {@link Configurable} means that a new instance of the specified class will be created each time when
 * the dialog is opened.
 * <p>
 * <li> an extension with 'provider' attribute<br>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;projectConfigurable provider="class-name"/&gt;<br>
 * &lt;/extensions&gt;<br>
 * where 'class-name' implements {@link ConfigurableProvider} means that method {@link ConfigurableProvider#createConfigurable()}
 * will be used to create instance each time when the dialog is opened.
 * </ul>
 */
public interface Configurable extends UnnamedConfigurable {
  /**
   * @deprecated projectConfigurable extensions aren't of type Configurable anymore
   */
  ExtensionPointName<Configurable> PROJECT_CONFIGURABLES = ExtensionPointName.create("com.intellij.projectConfigurable");
  /**
   * @deprecated applicationConfigurable extensions aren't of type Configurable anymore
   */
  ExtensionPointName<Configurable> APPLICATION_CONFIGURABLES = ExtensionPointName.create("com.intellij.applicationConfigurable");

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
  @Nullable Icon getIcon();

  /**
   * Returns the topic in the help file which is shown when help for the configurable
   * is requested.
   *
   * @return the help topic, or null if no help is available.
   */
  @Nullable
  @NonNls String getHelpTopic();

  /**
   * @deprecated
   */
  interface Assistant extends Configurable {
  }

  interface Composite {
    Configurable[] getConfigurables();
  }

  /**
   * Forbids wrapping the content of the configurable in a scroll pane. Required when
   * the configurable contains its own scrollable components.
   */
  interface NoScroll {
  }

}
