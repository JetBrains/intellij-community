// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.CoroutineContext

val <T : Any> ExtensionPointName<T>.extensionsFlow: Flow<List<T>>
  get() = callbackFlow {
    val listener = object : ExtensionPointListener<T> {
      override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
        trySendBlocking(extensions.toList())
      }

      override fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {
        trySendBlocking(extensions.toList())
      }
    }
    send(extensions.toList())
    addExtensionPointListener(listener)
    awaitClose { removeExtensionPointListener(listener) }
  }

@Service(Service.Level.PROJECT)
internal class DependencyToolwindowLifecycleScope : CoroutineScope, Disposable {

  val dispatcher = AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

  override val coroutineContext =
    SupervisorJob() + CoroutineName(this::class.qualifiedName!!) + dispatcher

  override fun dispose() {
    cancel("Disposing ${this::class.simpleName}")
  }
}

internal val Project.lifecycleScope: DependencyToolwindowLifecycleScope
  get() = service()

internal val Project.contentIdMap
  get() = service<ContentIdMapService>().idMap.value

fun <L : Any, K> Project.messageBusFlow(
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