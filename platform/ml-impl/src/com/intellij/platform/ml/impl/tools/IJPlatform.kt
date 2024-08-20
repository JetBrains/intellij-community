// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.util.application
import com.intellij.util.messages.Topic
import com.jetbrains.ml.platform.MLApiPlatform.ExtensionController
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
@Service(Service.Level.APP)
class IJPlatform : com.jetbrains.ml.platform.MLApiPlatform(
  featureProviders = EP_NAME_FEATURE_PROVIDER.extensionList,
  mlUnitProviders = emptyList(),
) {
  override val taskListeners: Map<String, List<com.jetbrains.ml.monitoring.MLTaskListenerTyped<*, *>>>
    get() = KeyedMessagingProvider.collect(MLTaskListenerTyped.TOPIC)

  override fun addTaskListener(taskId: String, taskListener: com.jetbrains.ml.monitoring.MLTaskListenerTyped<*, *>): com.jetbrains.ml.platform.MLApiPlatform.ExtensionController {
    val connection = application.messageBus.connect()

    fun <M : com.jetbrains.ml.model.MLModel<P>, P : Any> capturingType(taskListenerTyped: com.jetbrains.ml.monitoring.MLTaskListenerTyped<M, P>) {
      connection.subscribe(MLTaskListenerTyped.TOPIC, object : MessageBusMLTaskListenerProvider<M, P> {
        override fun provide(collector: (com.jetbrains.ml.monitoring.MLTaskListenerTyped<M, P>, String) -> Unit) = collector(taskListenerTyped, taskId)
      })
    }

    capturingType(taskListener)

    return ExtensionController { connection.disconnect() }
  }

  override val loggingListeners: Map<String, List<com.jetbrains.ml.monitoring.MLTaskLoggingListener>>
    get() = KeyedMessagingProvider.collect(MLTaskLoggingListener.TOPIC)

  override fun addLoggingListener(taskId: String, loggingListener: com.jetbrains.ml.monitoring.MLTaskLoggingListener): ExtensionController {
    val connection = application.messageBus.connect()
    connection.subscribe(MLTaskLoggingListener.TOPIC, object : MessageBusMLTaskLoggingListenerProvider {
      override fun provide(collector: (com.jetbrains.ml.monitoring.MLTaskLoggingListener, String) -> Unit) = collector(loggingListener, taskId)
    })

    return ExtensionController { connection.disconnect() }
  }

  override val systemLoggerBuilder: com.jetbrains.ml.platform.SystemLoggerBuilder = object : com.jetbrains.ml.platform.SystemLoggerBuilder {
    override fun build(clazz: Class<*>): com.jetbrains.ml.platform.SystemLogger {
      return IJSystemLogger(Logger.getInstance(clazz))
    }

    override fun build(name: String): com.jetbrains.ml.platform.SystemLogger {
      return IJSystemLogger(Logger.getInstance(name))
    }

    private inner class IJSystemLogger(private val baseLogger: Logger) : com.jetbrains.ml.platform.SystemLogger {
      override fun info(data: () -> String) = baseLogger.info(data())

      override fun warn(data: () -> String) = baseLogger.warn(data())

      override fun debug(data: () -> String) = baseLogger.debug { data() }

      override fun error(e: Throwable) = baseLogger.error(e)
    }
  }
}


@ApiStatus.Internal
sealed interface KeyedMessagingProvider<T> {
  fun provide(collector: (T, String) -> Unit)

  companion object {
    fun <P : KeyedMessagingProvider<*>, T> collect(topic: Topic<P>): Map<String, List<T>> {
      val collected = mutableMapOf<String, MutableList<T>>()
      application.messageBus.syncPublisher(topic).provide { it, keyOfIt ->
        @Suppress("UNCHECKED_CAST")
        collected.getOrPut(keyOfIt) { mutableListOf() }.add(it as T)
      }
      return collected
    }
  }
}

@ApiStatus.Internal
interface MessageBusMLTaskListenerProvider<M : com.jetbrains.ml.model.MLModel<P>, P : Any> : KeyedMessagingProvider<com.jetbrains.ml.monitoring.MLTaskListenerTyped<M, P>>

@ApiStatus.Internal
interface MessageBusMLTaskLoggingListenerProvider : KeyedMessagingProvider<com.jetbrains.ml.monitoring.MLTaskLoggingListener>
