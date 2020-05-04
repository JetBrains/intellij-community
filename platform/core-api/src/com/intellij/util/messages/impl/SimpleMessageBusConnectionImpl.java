// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.util.SmartFMap;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class SimpleMessageBusConnectionImpl implements SimpleMessageBusConnection, MessageBusImpl.MessageHandlerHolder {
  private MessageBusImpl myBus;
  private final AtomicReference<SmartFMap<Topic<?>, Object>> topicToHandlers = new AtomicReference<>(SmartFMap.emptyMap());

  SimpleMessageBusConnectionImpl(@NotNull MessageBusImpl bus) {
    myBus = bus;
  }

  @Override
  public <L> void subscribe(@NotNull Topic<L> topic, @NotNull L handler) {
    MessageBusConnectionImpl.doSubscribe(topic, handler, topicToHandlers);
    myBus.notifyOnSubscription(topic);
  }

  @Override
  public void collectHandlers(@NotNull Topic<?> topic, @NotNull List<Object> result) {
    Object handlers = topicToHandlers.get().get(topic);
    if (handlers != null) {
      if (handlers instanceof Object[]) {
        Collections.addAll(result, (Object[])handlers);
      }
      else {
        result.add(handlers);
      }
    }
  }

  @Override
  public void disconnect() {
    MessageBusImpl bus = myBus;
    if (bus == null) {
      return;
    }

    myBus = null;
    // reset as bus will not remove disposed connection from list immediately
    SmartFMap<Topic<?>, Object> oldMap = topicToHandlers.getAndSet(SmartFMap.emptyMap());

    bus.notifyConnectionTerminated(oldMap);
  }

  @Override
  public boolean isEmpty() {
    return topicToHandlers.get().isEmpty();
  }

  @Override
  public String toString() {
    return topicToHandlers.get().toString();
  }
}
