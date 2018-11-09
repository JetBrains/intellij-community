// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class UIPluginGroup {
  public Component panel;
  public List<CellPluginComponent> plugins = new ArrayList<>();

  @Nullable
  public CellPluginComponent findComponent(@NotNull IdeaPluginDescriptor descriptor) {
    String pluginId = descriptor.getPluginId().getIdString();
    for (CellPluginComponent component : plugins) {
      if (pluginId.equals(component.myPlugin.getPluginId().getIdString())) {
        return component;
      }
    }
    return null;
  }
}