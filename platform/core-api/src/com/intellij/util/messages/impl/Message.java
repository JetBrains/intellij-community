// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

final class Message {
  final Topic<?> topic;
  final Method listenerMethod;
  final Object[] args;
  final List<Object> handlers;

  // to avoid creating Message for each handler
  // see note about pumpMessages in createPublisher (invoking job handlers can be stopped and continued as part of another pumpMessages call)
  int currentHandlerIndex;

  Message(@NotNull Topic<?> topic, @NotNull Method listenerMethod, Object[] args, @NotNull List<Object> handlers) {
    this.topic = topic;
    listenerMethod.setAccessible(true);
    this.listenerMethod = listenerMethod;
    this.args = args;
    this.handlers = handlers;
  }

  @Override
  public String toString() {
    return "Message(" +
           "topic=" + topic +
           ", listenerMethod=" + listenerMethod +
           ", args=" + Arrays.toString(args) +
           ", handlers=" + handlers +
           ')';
  }
}
