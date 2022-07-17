// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.openapi.util.Disposer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.MessageHandler
import com.intellij.util.messages.Topic

internal class MessageBusConnectionImpl(bus: MessageBusImpl) : BaseBusConnection(bus), MessageBusConnection {
  private var defaultHandler: MessageHandler? = null

  override fun <L : Any> subscribe(topic: Topic<L>) {
    val defaultHandler = defaultHandler
                         ?: throw IllegalStateException("Connection must have default handler installed prior " +
                                                        "to any anonymous subscriptions. Target topic: $topic")
    check(!topic.listenerClass.isInstance(defaultHandler)) {
      "Can't subscribe to the topic '$topic'. " +
      "Default handler has incompatible type - expected: '${topic.listenerClass}', actual: '${defaultHandler.javaClass}'"
    }
    @Suppress("UNCHECKED_CAST")
    subscribe(topic, defaultHandler as L)
  }

  override fun setDefaultHandler(handler: MessageHandler?) {
    defaultHandler = handler
  }

  override fun dispose() {
    // already disposed
    val bus = bus ?: return
    this.bus = null
    defaultHandler = null
    // reset as bus will not remove disposed connection from list immediately
    bus.notifyConnectionTerminated(subscriptions.getAndSet(ArrayUtilRt.EMPTY_OBJECT_ARRAY))
  }

  override fun disconnect() {
    Disposer.dispose(this)
  }

  override fun deliverImmediately() {
    val bus = bus
    if (bus == null) {
      MessageBusImpl.LOG.error("Bus is already disposed")
    }
    else {
      bus.deliverImmediately(this)
    }
  }

  fun isMyHandler(topic: Topic<*>, handler: Any): Boolean {
    if (defaultHandler === handler) {
      return true
    }

    val topicAndHandlerPairs = subscriptions.get()
    var i = 0
    val n = topicAndHandlerPairs.size
    while (i < n) {
      if (topic === topicAndHandlerPairs[i] && handler === topicAndHandlerPairs[i + 1]) {
        return true
      }
      i += 2
    }
    return false
  }
}