// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.util.messages.Topic;


public interface CompletionPhaseListener {
  void completionPhaseChanged(boolean isCompletionRunning);

  @Topic.AppLevel
  Topic<CompletionPhaseListener> TOPIC = new Topic<>("CompletionPhaseListener",
                                                     CompletionPhaseListener.class,
                                                     Topic.BroadcastDirection.NONE);
}
