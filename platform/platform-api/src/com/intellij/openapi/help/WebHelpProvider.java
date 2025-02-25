// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * After that {@link #getHelpPageUrl(String)} method will be used to show help for topics which IDs start with {@code '<plugin ID>.'}.
 */
public abstract class WebHelpProvider implements PluginAware {
  private String myHelpTopicPrefix;

  /**
   * Return a URL of a page which should be opened in a browser when the context help for {@code helpTopicId} is invoked.
   * The method will be called only when {@code helpTopicId} starts with {@link #getHelpTopicPrefix()} prefix.
   *
   * @param helpTopicId full ID of a help topic including the prefix
   */
  public abstract @Nullable String getHelpPageUrl(@NotNull String helpTopicId);

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myHelpTopicPrefix = pluginDescriptor.getPluginId().getIdString() + '.';
  }

  public @NotNull String getHelpTopicPrefix() {
    return myHelpTopicPrefix;
  }
}
