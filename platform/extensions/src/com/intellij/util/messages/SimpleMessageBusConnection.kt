// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages

interface SimpleMessageBusConnection {
  /**
   * Subscribes given handler to the target endpoint within the current connection.
   *
   * @param topic   target endpoint
   * @param handler target handler to use for incoming messages
   * @param <L> interface for working with the target topic
   * @see MessageBus.syncPublisher
   */
  fun <L : Any> subscribe(topic: Topic<L>, handler: L)

  /**
   * Disconnects current connections from the [message bus][MessageBus] and drops all queued but not dispatched messages (if any)
   */
  fun disconnect()
}