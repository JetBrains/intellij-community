// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
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