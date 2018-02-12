/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.help;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Override this class and register the implementation in {@code plugin.xml} to provide custom context web help for your plugin:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;webHelpProvider implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * After that {@link #getHelpPageUrl(String)} method will be used to show help for topics which IDs start with '&lt;plugin ID&gt;.'.
 *
 * @author nik
 */
public abstract class WebHelpProvider implements PluginAware {
  private String myHelpTopicPrefix;

  /**
   * Return URL of page which should be opened in browser when context help for {@code helpTopicId} is invoked. The method will be called
   * only if {@code helpTopicId} starts with '&lt;plugin ID&gt;.' prefix.
   * @param helpTopicId full ID of help topic including '&lt;plugin ID&gt;.' prefix
   */
  @Nullable
  public abstract String getHelpPageUrl(@NotNull String helpTopicId);

  @Override
  public final void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myHelpTopicPrefix = pluginDescriptor.getPluginId().getIdString() + ".";
  }

  @NotNull
  public String getHelpTopicPrefix() {
    return myHelpTopicPrefix;
  }
}
