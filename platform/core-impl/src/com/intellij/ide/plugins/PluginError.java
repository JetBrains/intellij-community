// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PluginError {
  private final IdeaPluginDescriptorImpl plugin;
  private final String message;
  private final String incompatibleReason;

  public PluginError(@Nullable IdeaPluginDescriptorImpl plugin, @NotNull String message, @Nullable String incompatibleReason) {
    this.plugin = plugin;
    this.message = message;
    this.incompatibleReason = incompatibleReason;
  }

  @NotNull String toUserError() {
    if (plugin == null) {
      return message;
    }
    else if (incompatibleReason != null) {
      return "Plugin \"" + plugin.getName() + "\" is incompatible (" + incompatibleReason + ")";
    }
    else {
      return "Plugin \"" + plugin.getName() + "\" " + message;
    }
  }

  @Override
  public @NotNull String toString() {
    return plugin == null ? message : plugin.formatErrorMessage(message);
  }
}
