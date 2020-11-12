// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

abstract class BaseBusConnection implements MessageBusImpl.MessageHandlerHolder {
  protected MessageBusImpl bus;
  protected final AtomicReference</*(@NotNull Topic<L>, @NotNull L) pairs*/Object[]> subscriptions = new AtomicReference<>(ArrayUtilRt.EMPTY_OBJECT_ARRAY);

  BaseBusConnection(@NotNull MessageBusImpl bus) {
    this.bus = bus;
  }

  public final <L> void subscribe(@NotNull Topic<L> topic, @NotNull L handler) {
    Object[] list;
    Object[] newList;
    do {
      list = subscriptions.get();
      if (list.length == 0) {
        newList = new Object[]{topic, handler};
      }
      else {
        int size = list.length;
        newList = Arrays.copyOf(list, size + 2);
        newList[size] = topic;
        newList[size + 1] = handler;
      }
    }
    while (!subscriptions.compareAndSet(list, newList));
    bus.notifyOnSubscription(topic);
  }

  @Override
  public final <L> void collectHandlers(@NotNull Topic<L> topic, @NotNull List<? super L> result) {
    Object[] list = subscriptions.get();
    for (int i = 0, n = list.length; i < n; i += 2) {
      if (list[i] == topic) {
        //noinspection unchecked
        result.add((L)list[i + 1]);
      }
    }
  }

  @Override
  public final void disconnectIfNeeded(@NotNull Predicate<? super Class<?>> predicate) {
    while (true) {
      Object[] list = subscriptions.get();
      List<Object> newList = null;
      for (int i = 0; i < list.length; i += 2) {
        if (predicate.test(list[i + 1].getClass())) {
          if (newList == null) {
            newList = new ArrayList<>(Arrays.asList(list).subList(0, i));
          }
        }
        else if (newList != null) {
          newList.add(list[i]);
          newList.add(list[i + 1]);
        }
      }

      if (newList == null) {
        return;
      }

      if (newList.isEmpty()) {
        disconnect();
        return;
      }
      else if (subscriptions.compareAndSet(list, newList.toArray())) {
        break;
      }
    }
  }

  protected abstract void disconnect();

  @Override
  public final boolean isDisposed() {
    return bus == null;
  }

  @Override
  public final String toString() {
    return Arrays.toString(subscriptions.get());
  }
}
