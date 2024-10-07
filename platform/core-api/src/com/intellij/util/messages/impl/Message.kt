// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.util.messages.Topic
import java.lang.invoke.MethodHandle

internal class Message(
  @JvmField val topic: Topic<*>,
  // we don't bind args as part of MethodHandle creation, because an object is not known yet - so, MethodHandle here is not ready to use
  @JvmField val method: MethodHandle,
  @JvmField val methodName: String,
  // it allows us to cache MethodHandle per method and partially reuse it
  @JvmField val args: Array<Any?>?,
  @JvmField val handlers: Array<Any?>,
  @JvmField val bus: MessageBusImpl,
) {
  @JvmField
  val clientId: String = ClientId.getCurrentValue()

  // To avoid creating Message for each handler.
  // See note about pumpMessages in createPublisher
  // (invoking job handlers can be stopped and continued as part of another pumpMessages call).
  @JvmField var currentHandlerIndex: Int = 0

  override fun toString(): String {
    return "Message(topic=$topic, method=$methodName, args=${args.contentToString()}, handlers=${handlers.contentToString()})"
  }
}