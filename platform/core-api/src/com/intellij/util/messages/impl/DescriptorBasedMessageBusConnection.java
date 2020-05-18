// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class DescriptorBasedMessageBusConnection implements MessageBusImpl.MessageHandlerHolder {
  final PluginId pluginId;
  final Topic<?> topic;
  final List<Object> handlers;

  DescriptorBasedMessageBusConnection(@NotNull PluginId pluginId, @NotNull Topic<?> topic, @NotNull List<Object> handlers) {
    this.pluginId = pluginId;
    this.topic = topic;
    this.handlers = handlers;
  }

  @Override
  public void collectHandlers(@NotNull Topic<?> topic, @NotNull List<Object> result) {
    if (this.topic == topic) {
      result.addAll(handlers);
    }
  }

  @Override
  public void disconnectIfNeeded(@NotNull Predicate<Class<?>> predicate) {
  }

  @Override
  public boolean isDisposed() {
    // never empty
    return false;
  }

  @Override
  public String toString() {
    return "DescriptorBasedMessageBusConnection(" +
           "handlers=" + handlers +
           ')';
  }

  static @Nullable List<Object> computeNewHandlers(@NotNull List<Object> handlers, @NotNull Set<String> excludeClassNames) {
    List<Object> newHandlers = null;
    for (int i = 0, size = handlers.size(); i < size; i++) {
      Object handler = handlers.get(i);
      if (excludeClassNames.contains(handler.getClass().getName())) {
        if (newHandlers == null) {
          newHandlers = i == 0 ? new ArrayList<>() : new ArrayList<>(handlers.subList(0, i));
        }
      }
      else if (newHandlers != null) {
        newHandlers.add(handler);
      }
    }
    return newHandlers;
  }
}
