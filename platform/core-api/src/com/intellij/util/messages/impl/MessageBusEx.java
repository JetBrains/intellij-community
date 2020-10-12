// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.messages.ListenerDescriptor;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@ApiStatus.Internal
public interface MessageBusEx extends MessageBus {
  void clearPublisherCache();

  void unsubscribeLazyListeners(@NotNull PluginId pluginId, @NotNull List<ListenerDescriptor> listenerDescriptors);

  /**
   * Must be called only on a root bus.
   */
  void disconnectPluginConnections(@NotNull Predicate<? super Class<?>> pluginId);

  @TestOnly
  void clearAllSubscriberCache();

  void setLazyListeners(@NotNull Map<String, List<ListenerDescriptor>> map);
}
