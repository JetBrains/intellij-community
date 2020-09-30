// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this class in your plugin if there is another plugin whose functionality covers functionality provided by this plugin,
 * so there is no sense to have both plugins installed simultaneously.
 * The IDE then suggests the user disable this plugin when she downloads the new plugin in Settings | Plugins.
 * <p/>
 * The implementation must be registered in plugin.xml of the plugin you want to replace:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;pluginReplacement implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 */
public abstract class PluginReplacement implements PluginAware {
  public static final ExtensionPointName<PluginReplacement> EP_NAME = ExtensionPointName.create("com.intellij.pluginReplacement");
  private final String myNewPluginId;
  private PluginDescriptor myPluginDescriptor;

  protected PluginReplacement(String newPluginId) {
    myNewPluginId = newPluginId;
  }

  @NotNull
  @Nls
  public String getReplacementMessage(@NotNull IdeaPluginDescriptor oldPlugin, @NotNull IdeaPluginDescriptor newPlugin) {
    return IdeBundle.message("plugin.manager.replace.plugin.0.by.plugin.1", oldPlugin.getName(), newPlugin.getName());
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public final PluginDescriptor getOldPluginDescriptor() {
    return myPluginDescriptor;
  }

  public final String getNewPluginId() {
    return myNewPluginId;
  }
}
