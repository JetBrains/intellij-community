// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MessageBusConnectionImpl extends BaseBusConnection implements MessageBusConnection {
  private MessageHandler defaultHandler;

  MessageBusConnectionImpl(@NotNull MessageBusImpl bus) {
    super(bus);
  }

  @Override
  public <L> void subscribe(@NotNull Topic<L> topic) throws IllegalStateException {
    MessageHandler defaultHandler = this.defaultHandler;
    if (defaultHandler == null) {
      throw new IllegalStateException("Connection must have default handler installed prior to any anonymous subscriptions. "
                                      + "Target topic: " + topic);
    }
    if (topic.getListenerClass().isInstance(defaultHandler)) {
      throw new IllegalStateException("Can't subscribe to the topic '" + topic + "'. Default handler has incompatible type - expected: '" +
                                      topic.getListenerClass() + "', actual: '" + defaultHandler.getClass() + "'");
    }

    //noinspection unchecked
    subscribe(topic, (L)defaultHandler);
  }

  @Override
  public void setDefaultHandler(MessageHandler handler) {
    defaultHandler = handler;
  }

  @Override
  public void dispose() {
    MessageBusImpl bus = this.bus;
    if (bus == null) {
      // already disposed
      return;
    }

    this.bus = null;
    defaultHandler = null;
    // reset as bus will not remove disposed connection from list immediately
    bus.notifyConnectionTerminated(subscriptions.getAndSet(ArrayUtilRt.EMPTY_OBJECT_ARRAY));
  }

  @Override
  public void disconnect() {
    Disposer.dispose(this);
  }

  @Override
  public void deliverImmediately() {
    bus.deliverImmediately(this);
  }

  /**
   * Returns true if no more handlers.
   */
  static boolean nullizeHandlersFromMessage(@NotNull Message message, @Nullable Object @NotNull [] topicAndHandlerPairs) {
    int nullElementCount = 0;
    for (int messageIndex = 0; messageIndex < message.handlers.length; messageIndex++) {
      Object handler = message.handlers[messageIndex];
      if (handler == null) {
        nullElementCount++;
      }

      for (int i = 0; i < topicAndHandlerPairs.length; i +=2) {
        if (message.topic == topicAndHandlerPairs[i] && handler == topicAndHandlerPairs[i + 1]) {
          message.handlers[messageIndex] = null;
          nullElementCount++;
        }
      }
    }
    return nullElementCount == message.handlers.length;
  }

  boolean isMyHandler(@NotNull Topic<?> topic, @NotNull Object handler) {
    if (defaultHandler == handler) {
      return true;
    }

    Object[] topicAndHandlerPairs = subscriptions.get();
    for (int i = 0, n = topicAndHandlerPairs.length; i < n; i += 2) {
      if (topic == topicAndHandlerPairs[i] && handler == topicAndHandlerPairs[i + 1]) {
        return true;
      }
    }
    return false;
  }
}
