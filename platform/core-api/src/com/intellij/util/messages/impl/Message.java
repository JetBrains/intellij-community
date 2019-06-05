// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class Message {
  private final Topic myTopic;
  private final Method myListenerMethod;
  private final Object[] myArgs;

  public Message(@NotNull Topic topic, @NotNull Method listenerMethod, Object[] args) {
    myTopic = topic;
    listenerMethod.setAccessible(true);
    myListenerMethod = listenerMethod;
    myArgs = args;
  }

  @NotNull
  public Topic getTopic() {
    return myTopic;
  }

  @NotNull
  public Method getListenerMethod() {
    return myListenerMethod;
  }

  public Object[] getArgs() {
    return myArgs;
  }

  @Override
  public String toString() {
    return myTopic + ":" + myListenerMethod.getName();
  }
}
