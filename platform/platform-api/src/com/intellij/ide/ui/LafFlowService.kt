// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service(Service.Level.APP)
class LafFlowService(
  private val scope: CoroutineScope,
) {
  private val lafEventFlow = ApplicationManager.getApplication().lookAndFeelFlow(scope)

  @Suppress("MemberVisibilityCanBePrivate")
  val lafChangedFlow: SharedFlow<Unit> = lafEventFlow.onStart { emit(Unit) }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

  fun <T> customLafFlowState(
    default: T,
    sharingStarted: SharingStarted = SharingStarted.Eagerly,
    mapper: (SharedFlow<Unit>) -> Flow<T>,
  ): StateFlow<T> {
    return mapper(lafChangedFlow).stateIn(scope, sharingStarted, default)
  }

  companion object {
    @JvmStatic
    fun getInstance(): LafFlowService = service<LafFlowService>()
  }
}

private fun Application.lookAndFeelFlow(scope: CoroutineScope): Flow<Unit> =
  messageBus.flow(LafManagerListener.TOPIC, scope) { LafManagerListener { trySend(Unit) } }

private fun <L : Any, K> MessageBus.flow(
  topic: Topic<L>,
  parentScope: CoroutineScope,
  listener: ProducerScope<K>.() -> L,
): Flow<K> = callbackFlow {
  val connection: SimpleMessageBusConnection = connect(parentScope)
  connection.subscribe(topic, listener())
  awaitClose { connection.disconnect() }
}
