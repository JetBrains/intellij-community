// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;

final class Message {
  final Topic<?> topic;
  final String methodName;
  final MethodHandle method;
  // we don't bind args as part of MethodHandle creation, because object is not known yet - so, MethodHandle here is not ready to use
  // it allows us to cache MethodHandle per method and partially reuse it
  final Object[] args;
  final @Nullable Object @NotNull [] handlers;
  @NotNull
  final String clientId;

  // to avoid creating Message for each handler
  // see note about pumpMessages in createPublisher (invoking job handlers can be stopped and continued as part of another pumpMessages call)
  int currentHandlerIndex;

  Message(@NotNull Topic<?> topic, @NotNull MethodHandle method, @NotNull String methodName, Object[] args, @Nullable Object @NotNull [] handlers) {
    this.topic = topic;
    this.method = method;
    this.methodName = methodName;
    this.args = args;
    this.handlers = handlers;
    clientId = ClientId.getCurrentValue();
  }

  @Override
  public String toString() {
    return "Message(" +
           "topic=" + topic +
           ", method=" + methodName +
           ", args=" + Arrays.toString(args) +
           ", handlers=" + Arrays.toString(handlers) +
           ')';
  }
}
