// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author yole
 */
public class MessageListenerList<T> {
  private final MessageBus myMessageBus;
  private final Topic<T> myTopic;
  private final Map<T, MessageBusConnection> myListenerToConnectionMap = ContainerUtil.newConcurrentMap();

  public MessageListenerList(@NotNull MessageBus messageBus, @NotNull Topic<T> topic) {
    myTopic = topic;
    myMessageBus = messageBus;
  }

  public void add(@NotNull T listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(myTopic, listener);
    myListenerToConnectionMap.put(listener, connection);
  }

  public void add(@NotNull final T listener, @NotNull Disposable parentDisposable) {
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myListenerToConnectionMap.remove(listener);
      }
    });
    final MessageBusConnection connection = myMessageBus.connect(parentDisposable);
    connection.subscribe(myTopic, listener);
    myListenerToConnectionMap.put(listener, connection);
  }

  public void remove(@NotNull T listener) {
    final MessageBusConnection connection = myListenerToConnectionMap.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }
}
