// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.util.messages.Topic;

import java.util.EventListener;
import java.util.UUID;

public interface CustomBuilderMessageHandler extends EventListener {

  /**
   * Custom builder message.
   */
  @Topic.ProjectLevel
  Topic<CustomBuilderMessageHandler> TOPIC = new Topic<>(CustomBuilderMessageHandler.class, Topic.BroadcastDirection.NONE);

  default void messageReceived(UUID sessionId, String builderId, String messageType, String messageText) {
    messageReceived(builderId, messageType, messageText);
  }

  void messageReceived(String builderId, String messageType, String messageText);
}
