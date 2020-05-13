// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages;

import org.jetbrains.annotations.NotNull;

public interface SimpleMessageBusConnection {
  /**
   * Subscribes given handler to the target endpoint within the current connection.
   *
   * @param topic   target endpoint
   * @param handler target handler to use for incoming messages
   * @param <L>     interface for working with the target topic
   * @throws IllegalStateException if there is already registered handler for the target endpoint within the current connection.
   *                               Note that that previously registered handler is not replaced by the given one then
   * @see MessageBus#syncPublisher(Topic)
   * @see com.intellij.application.Topics#subscribe
   */
  <L> void subscribe(@NotNull Topic<L> topic, @NotNull L handler) throws IllegalStateException;

  /**
   * Disconnects current connections from the {@link MessageBus message bus} and drops all queued but not dispatched messages (if any)
   */
  void disconnect();
}
