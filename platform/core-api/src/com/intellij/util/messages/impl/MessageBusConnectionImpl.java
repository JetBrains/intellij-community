// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartFMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class MessageBusConnectionImpl implements MessageBusConnection, MessageBusImpl.MessageHandlerHolder {
  private final MessageBusImpl myBus;

  private MessageHandler myDefaultHandler;
  private final AtomicReference<SmartFMap<Topic<?>, Object>> mySubscriptions = new AtomicReference<>(SmartFMap.emptyMap());

  MessageBusConnectionImpl(@NotNull MessageBusImpl bus) {
    myBus = bus;
  }

  @Override
  public <L> void subscribe(@NotNull Topic<L> topic, @NotNull L handler) {
    Object newHandlers;
    SmartFMap<Topic<?>, Object> map;
    do {
      map = mySubscriptions.get();
      Object currentHandler = map.get(topic);
      if (currentHandler == null) {
        newHandlers = handler;
      }
      else if (currentHandler instanceof Object[]) {
        newHandlers = ArrayUtil.append((Object[])currentHandler, handler, Object[]::new);
      }
      else {
        newHandlers = new Object[]{currentHandler, handler};
      }
    }
    while (!mySubscriptions.compareAndSet(map, map.plus(topic, newHandlers)));

    myBus.notifyOnSubscription(topic);
  }

  @Override
  public void collectHandlers(@NotNull Topic<?> topic, @NotNull List<Object> result) {
    Object handlers = mySubscriptions.get().get(topic);
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
  public <L> void subscribe(@NotNull Topic<L> topic) throws IllegalStateException {
    MessageHandler defaultHandler = myDefaultHandler;
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
    myDefaultHandler = handler;
  }

  @Override
  public void dispose() {
    myBus.notifyConnectionTerminated(this);
  }

  @Override
  public void disconnect() {
    Disposer.dispose(this);
  }

  @Override
  public void deliverImmediately() {
    myBus.deliverImmediately(this);
  }

  void removeMyHandlers(@NotNull Message job) {
    List<Object> jobHandlers = job.handlers;
    if (myDefaultHandler != null) {
      jobHandlers.removeIf(handler -> handler == myDefaultHandler);
    }

    Object handlers = mySubscriptions.get().get(job.topic);
    if (handlers == null) {
      return;
    }

    if (handlers instanceof Object[]) {
      jobHandlers.removeIf(handler -> containsByIdentity(handler, (Object[])handlers));
    }
    else {
      jobHandlers.removeIf(handler -> handlers == handler);
    }
  }

  boolean isEmpty() {
    return mySubscriptions.get().isEmpty();
  }

  boolean isBroadCastDisabled() {
    for (Topic<?> topic : mySubscriptions.get().keySet()) {
      if (topic.getBroadcastDirection() != Topic.BroadcastDirection.NONE) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return mySubscriptions.get().toString();
  }

  boolean isMyHandler(@NotNull Topic<?> topic, @NotNull Object handler) {
    if (myDefaultHandler == handler) {
      return true;
    }

    Object handlers = mySubscriptions.get().get(topic);
    if (handlers == null) {
      return false;
    }
    return handlers == handler || (handlers instanceof Object[] && containsByIdentity(handler, (Object[])handlers));
  }

  private static boolean containsByIdentity(@NotNull Object handler, @NotNull Object[] handlers) {
    for (Object item : handlers) {
      if (handler == item) {
        return true;
      }
    }
    return false;
  }
}
