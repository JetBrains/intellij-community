// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;

/**
 * Describes a plugin which may be installed into IntelliJ-based IDE. Use {@link com.intellij.ide.plugins.PluginManagerCore#getPlugin(PluginId)}
 * to get a descriptor by a plugin ID and {@link com.intellij.ide.plugins.PluginManagerCore#getPlugins()} to get all plugins.
 */
public interface IdeaPluginDescriptor extends PluginDescriptor {
}