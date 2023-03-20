// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages

import com.intellij.openapi.Disposable

/**
 * Aggregates multiple topic subscriptions for particular [message bus][MessageBus]. I.e. every time a client wants to
 * listen for messages it should grab appropriate connection (or create a new one) and [subscribe][.subscribe]
 * to particular endpoint.
 */
interface MessageBusConnection : SimpleMessageBusConnection, Disposable {
  /**
   * Subscribes to the target topic within the current connection using [default handler][.setDefaultHandler].
   *
   * @param topic  target endpoint
   * @param <L>    interface for working with the target topic
   * @throws IllegalStateException    if [default handler][.setDefaultHandler] hasn't been defined or
   * has incompatible type with the [topic&#39;s business interface][Topic.getListenerClass]
   * or if target topic is already subscribed within the current connection </L>
   */
  fun <L : Any> subscribe(topic: Topic<L>)

  /**
   * Allows specifying default handler to use during [anonymous subscriptions][.subscribe].
   */
  fun setDefaultHandler(handler: MessageHandler?)

  fun setDefaultHandler(runnable: Runnable) {
    setDefaultHandler { _, _ -> runnable.run() }
  }

  /**
   * Forces to process any queued but not delivered events.
   *
   * @see MessageBus.syncPublisher
   */
  fun deliverImmediately()
}