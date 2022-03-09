// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class DescriptorBasedMessageBusConnection<L> implements MessageBusImpl.MessageHandlerHolder {
  final @NotNull PluginDescriptor module;
  final @NotNull Topic<L> topic;
  final @NotNull List<? extends L> handlers;

  DescriptorBasedMessageBusConnection(@NotNull PluginDescriptor module,
                                      @NotNull Topic<L> topic,
                                      @NotNull List<? extends L> handlers) {
    this.module = module;
    this.topic = topic;
    this.handlers = handlers;
  }

  @Override
  public <L1> void collectHandlers(@NotNull Topic<L1> topic, @NotNull List<? super L1> result) {
    if (this.topic == topic) {
      //noinspection unchecked
      result.addAll((Collection<L1>)handlers);
    }
  }

  @Override
  public void disconnectIfNeeded(@NotNull Predicate<? super Class<?>> predicate) {
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

  static @Nullable <L> List<L> computeNewHandlers(@NotNull List<? extends L> handlers, @NotNull Set<String> excludeClassNames) {
    List<L> newHandlers = null;
    for (int i = 0, size = handlers.size(); i < size; i++) {
      L handler = handlers.get(i);
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
