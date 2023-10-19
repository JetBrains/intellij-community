// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

public interface DebuggerManagerListener extends EventListener {

  @Topic.ProjectLevel
  Topic<DebuggerManagerListener> TOPIC =
    new Topic<>("DebuggerManagerListener", DebuggerManagerListener.class, Topic.BroadcastDirection.NONE);

  default void sessionCreated(DebuggerSession session) {
  }

  default void sessionAttached(DebuggerSession session) {
  }

  default void sessionDetached(DebuggerSession session) {
  }

  default void sessionRemoved(DebuggerSession session) {
  }
}
