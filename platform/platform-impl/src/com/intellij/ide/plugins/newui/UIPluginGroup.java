// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class UIPluginGroup {
  public Component panel;
  public List<ListPluginComponent> plugins = new ArrayList<>();

  @Nullable
  public ListPluginComponent findComponent(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    for (ListPluginComponent component : plugins) {
      if (pluginId == component.myPlugin.getPluginId()) {
        return component;
      }
    }
    return null;
  }
}