// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

public interface DebuggerManagerListener extends EventListener {
  Topic<DebuggerManagerListener> TOPIC = new Topic<>("DebuggerManagerListener", DebuggerManagerListener.class);

  default void sessionCreated(DebuggerSession session) {
  }

  default void sessionAttached(DebuggerSession session) {
  }

  default void sessionDetached(DebuggerSession session) {
  }

  default void sessionRemoved(DebuggerSession session) {
  }
}
