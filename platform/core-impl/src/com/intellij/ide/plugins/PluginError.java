// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PluginError {
  final IdeaPluginDescriptorImpl plugin;
  private final @Nls String message;
  private final @Nls String incompatibleReason;
  private final boolean myNotifyUser;
  private PluginId myDisabledDependency;

  PluginError(@Nullable IdeaPluginDescriptorImpl plugin, @NotNull @Nls String message, @Nullable @Nls String incompatibleReason) {
    this(plugin, message, incompatibleReason, true);
  }

  PluginError(@Nullable IdeaPluginDescriptorImpl plugin,
                     @NotNull String message,
                     @Nullable String incompatibleReason,
                     boolean notifyUser) {
    this.plugin = plugin;
    this.message = message;
    this.incompatibleReason = incompatibleReason;
    myNotifyUser = notifyUser;
  }

  void setDisabledDependency(PluginId disabledDependency) {
    myDisabledDependency = disabledDependency;
  }

  @NotNull @Nls String toUserError() {
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

  String getMessage() {
    return message;
  }

  String getIncompatibleReason() {
    return incompatibleReason;
  }

  PluginId getDisabledDependency() {
    return myDisabledDependency;
  }

  boolean isNotifyUser() {
    return myNotifyUser;
  }

  @Override
  public @NotNull String toString() {
    return plugin == null ? message : plugin.formatErrorMessage(message);
  }
}
