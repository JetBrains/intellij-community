// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Subscribes to the connection bus for the specified [topic].
 * If you need to provide an initial value you can emit it before returning a listener from the lambda.
 *
 * Example:
 * ```
 *    messageBus.subscribeAsFlow(MyListener.TOPIC) {
 *       // optional initial value
 *       trySend(getInitialValue())
 *       object : MyListener {
 *         override fun eventTriggered(value: Value) {
 *            trySend(value)
 *         }
 *       }
 * ```
 */
fun <TListener : Any, TValue> MessageBus.subscribeAsFlow(
  topic: Topic<TListener>,
  listenerProvider: suspend ProducerScope<TValue>.() -> TListener
): Flow<TValue> = callbackFlow {
  val connection = simpleConnect()
  connection.subscribe(topic, listenerProvider())
  awaitClose { connection.disconnect() }
}