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
  public boolean excluded;

  public @Nullable ListPluginComponent findComponent(@NotNull IdeaPluginDescriptor descriptor) {
    return findComponent(descriptor.getPluginId());
  }

  public @Nullable ListPluginComponent findComponent(@NotNull PluginId pluginId) {
    for (ListPluginComponent component : plugins) {
      if (pluginId.equals(component.getPluginDescriptor().getPluginId())) {
        return component;
      }
    }
    return null;
  }
}