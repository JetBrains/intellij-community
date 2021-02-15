// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

public final class PluginLoadingError {
  private final @NotNull IdeaPluginDescriptor myPlugin;
  private final Supplier<@NlsContexts.DetailedDescription String> myDetailedMessage;
  private final Supplier<@NlsContexts.Label String> myShortMessage;
  private final boolean myNotifyUser;
  private PluginId myDisabledDependency;

  static PluginLoadingError create(@NotNull IdeaPluginDescriptor plugin,
                                   @NotNull Supplier<@NlsContexts.DetailedDescription String> detailedMessage,
                                   @NotNull Supplier<@NlsContexts.Label @NotNull String> shortMessage) {
    return new PluginLoadingError(plugin, detailedMessage, shortMessage, true);
  }

  static PluginLoadingError create(@NotNull IdeaPluginDescriptor plugin,
                                   @NotNull Supplier<@NlsContexts.DetailedDescription @NotNull String> detailedMessage,
                                   @NotNull Supplier<@NlsContexts.Label @NotNull String> shortMessage,
                                   boolean notifyUser) {
    return new PluginLoadingError(plugin, detailedMessage, shortMessage, notifyUser);
  }

  static PluginLoadingError createWithoutNotification(@NotNull IdeaPluginDescriptor plugin,
                                                      @NotNull Supplier<@NlsContexts.Label @NotNull String> shortMessage) {
    return new PluginLoadingError(plugin, null, shortMessage, false);
  }

  private PluginLoadingError(@NotNull IdeaPluginDescriptor plugin,
                             Supplier<String> detailedMessage,
                             Supplier<String> shortMessage,
                             boolean notifyUser) {
    myPlugin = plugin;
    myDetailedMessage = detailedMessage;
    myShortMessage = shortMessage;
    myNotifyUser = notifyUser;
  }

  void setDisabledDependency(PluginId disabledDependency) {
    myDisabledDependency = disabledDependency;
  }

  @NlsContexts.DetailedDescription
  public String getDetailedMessage() {
    return myDetailedMessage.get();
  }

  PluginId getDisabledDependency() {
    return myDisabledDependency;
  }

  boolean isNotifyUser() {
    return myNotifyUser;
  }

  void register(Map<PluginId, PluginLoadingError> errorsMap) {
    errorsMap.put(myPlugin.getPluginId(), this);
  }

  @Override
  public @NotNull String toString() {
    return getInternalMessage();
  }

  @NotNull
  public @NonNls String getInternalMessage() {
    return formatErrorMessage(myPlugin, (myDetailedMessage == null ? myShortMessage : myDetailedMessage).get());
  }

  public @NlsContexts.Label String getShortMessage() {
    return myShortMessage.get();
  }

  @NonNls @NotNull
  static String formatErrorMessage(IdeaPluginDescriptor descriptor, @NotNull String message) {
    String path = descriptor.getPluginPath().toString();
    StringBuilder builder = new StringBuilder();
    builder.append("The ").append(descriptor.getName()).append(" (id=").append(descriptor.getPluginId()).append(", path=");
    builder.append(FileUtil.getLocationRelativeToUserHome(path, false));
    String version = descriptor.getVersion();
    if (version != null && !descriptor.isBundled() && !version.equals(PluginManagerCore.getBuildNumber().asString())) {
      builder.append(", version=").append(version);
    }
    builder.append(") plugin ").append(message);
    return builder.toString();
  }
}
