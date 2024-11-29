package org.jetbrains.jewel.bridge

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

internal val IntelliJApplication: Application
    get() = ApplicationManager.getApplication()

private fun Application.lookAndFeelFlow(scope: CoroutineScope): Flow<Unit> =
    messageBus.flow(LafManagerListener.TOPIC, scope) { LafManagerListener { trySend(Unit) } }

internal fun Application.lookAndFeelChangedFlow(
    scope: CoroutineScope,
    sharingStarted: SharingStarted = SharingStarted.Eagerly,
): Flow<Unit> = lookAndFeelFlow(scope).onStart { emit(Unit) }.shareIn(scope, sharingStarted, replay = 1)

internal fun <L : Any, K> MessageBus.flow(
    topic: Topic<L>,
    parentScope: CoroutineScope,
    listener: ProducerScope<K>.() -> L,
): Flow<K> = callbackFlow {
    val connection: SimpleMessageBusConnection = connect(parentScope)
    connection.subscribe(topic, listener())
    awaitClose { connection.disconnect() }
}
