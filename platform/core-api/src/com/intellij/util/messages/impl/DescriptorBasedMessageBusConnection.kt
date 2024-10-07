// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusImpl.MessageHandlerHolder
import java.util.function.Predicate

internal class DescriptorBasedMessageBusConnection(
  @JvmField val module: PluginDescriptor,
  @JvmField val topic: Topic<*>,
  @JvmField val handlers: List<Any>,
) : MessageHandlerHolder {
  override fun collectHandlers(topic: Topic<*>, result: MutableList<in Any>) {
    if (this.topic === topic) {
      result.addAll(handlers)
    }
  }

  override fun disconnectIfNeeded(predicate: Predicate<Class<*>>) {
  }

  // never empty
  override val isDisposed: Boolean
    get() = false

  override fun toString(): String = "DescriptorBasedMessageBusConnection(handlers=$handlers)"
}