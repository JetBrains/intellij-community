// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.editor.Editor;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Reports typing latency measurements on the application-level {@link com.intellij.util.messages.MessageBus}.
 */
public interface LatencyListener {

  @Topic.AppLevel
  Topic<LatencyListener> TOPIC = new Topic<>("Typing latency notifications", LatencyListener.class, Topic.BroadcastDirection.NONE);

  /** Record latency for a single key typed. */
  void recordTypingLatency(@NotNull Editor editor, String action, long latencyMs);
}
