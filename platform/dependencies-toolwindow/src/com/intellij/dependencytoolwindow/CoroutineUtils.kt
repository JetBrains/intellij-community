// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.CoroutineContext

val <T : Any> ExtensionPointName<T>.extensionsFlow: Flow<List<T>>
  get() {
    return callbackFlow {
      val listener = object : ExtensionPointListener<T> {
        override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
          trySendBlocking(extensionList)
        }

        override fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {
          trySendBlocking(extensionList)
        }
      }
      send(extensionList)
      addExtensionPointListener(listener)
      awaitClose { removeExtensionPointListener(listener) }
    }
  }

internal fun Project.onDispose(action: () -> Unit) = Disposer.register(this, action)

internal fun <L : Any, K> Project.messageBusFlow(
  topic: Topic<L>,
  initialValue: (suspend () -> K)? = null,
  listener: suspend ProducerScope<K>.() -> L
): Flow<K> {
  return callbackFlow {
    initialValue?.let { send(it()) }
    val connection = messageBus.simpleConnect()
    connection.subscribe(topic, listener())
    awaitClose { connection.disconnect() }
  }
}

internal val Project.lookAndFeelFlow: Flow<LafManager>
  get() = messageBusFlow(LafManagerListener.TOPIC, { LafManager.getInstance()!! }) {
    LafManagerListener { trySend(it) }
  }

internal fun DependenciesToolWindowTabProvider.isAvailableFlow(project: Project): Flow<Boolean> {
  return callbackFlow {
    val sub = addIsAvailableChangesListener(project) { trySend(it) }
    awaitClose { sub.unsubscribe() }
  }
}

@Suppress("UnusedReceiverParameter")
internal fun Dispatchers.toolWindowManager(project: Project): CoroutineDispatcher = object : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: java.lang.Runnable) = ToolWindowManager.getInstance(project).invokeLater(block)
}