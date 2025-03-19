// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.platform.ml.*
import com.intellij.platform.ml.environment.EnvironmentExtender
import com.intellij.platform.ml.impl.ReplaceableIJPlatform.replacingWith
import com.intellij.platform.ml.monitoring.MLTaskGroupListener
import com.intellij.util.application
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
fun interface MessagingProvider<T> {
  fun provide(collector: (T) -> Unit)

  companion object {
    inline fun <T, reified P : MessagingProvider<T>> createTopic(displayName: @NonNls String): Topic<P> {
      return Topic.create(displayName, P::class.java)
    }

    fun <T, P : MessagingProvider<T>> collect(topic: Topic<P>): List<T> {
      val collected = mutableListOf<T>()
      application.messageBus.syncPublisher(topic).provide { collected.add(it) }
      return collected
    }
  }
}

@ApiStatus.Internal
fun interface MLTaskGroupListenerProvider : MessagingProvider<MLTaskGroupListener> {
  companion object {
    val TOPIC = MessagingProvider.createTopic<MLTaskGroupListener, MLTaskGroupListenerProvider>("ml.task")
  }
}

/**
 * A representation of the "real-life" [MLApiPlatform], whose content is
 * the content of the corresponding Extension Points.
 * It is used at the API's entry point, unless it is not replaced by another.
 *
 * It shouldn't be used due to low testability.
 * Use [ReplaceableIJPlatform] instead.
 */
@ApiStatus.Internal
private data object IJPlatform : MLApiPlatform() {
  override val tierDescriptors: List<TierDescriptor>
    get() = EP_NAME_TIER_DESCRIPTOR.extensionList

  override val environmentExtenders: List<EnvironmentExtender<*>>
    get() = EP_NAME_ENVIRONMENT_EXTENDER.extensionList

  override val taskApproaches: List<MLTaskApproachBuilder<*>>
    get() = EP_NAME_APPROACH_BUILDER.extensionList

  override val taskListeners: List<MLTaskGroupListener>
    get() = MessagingProvider.collect(MLTaskGroupListenerProvider.TOPIC)

  override fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController {
    val connection = application.messageBus.connect()
    connection.subscribe(MLTaskGroupListenerProvider.TOPIC, MLTaskGroupListenerProvider { it(taskListener) })
    return object : ExtensionController {
      override fun remove() {
        connection.disconnect()
      }
    }
  }

  override val coroutineScope: CoroutineScope
    get() = service<MLApiPlatformComputations>().coroutineScope

  override val systemLoggerBuilder: SystemLoggerBuilder = object : SystemLoggerBuilder {
    override fun build(clazz: Class<*>): SystemLogger {
      val ijLogger = com.intellij.openapi.diagnostic.Logger.getInstance(clazz)
      return object : SystemLogger {
        override fun info(data: () -> String) {
          ijLogger.info(data())
        }

        override fun debug(data: () -> String) {
          ijLogger.debug { data() }
        }
      }
    }
  }
}

@Service
private class MLApiPlatformComputations(val coroutineScope: CoroutineScope)

/**
 * Also a "real-life" [MLApiPlatform], but it can be replaced with another one any time.
 *
 * We always want to test [com.intellij.platform.ml.MLTaskApproach]es.
 * But after they are initialized by [com.intellij.platform.ml.MLTaskApproachBuilder],
 * the passed [MLApiPlatform] could already spread all the way within the API.
 * But the user-defined instances of the api could be overridden for testing sake.
 *
 * To replace all [TierDescriptor], [EnvironmentExtender] and [MLTaskApproachBuilder]
 * to test your code, you may call [replacingWith] and pass the desired environment,
 * that contains all the objects you need for your test.
 */
@ApiStatus.Internal
object ReplaceableIJPlatform : MLApiPlatform() {
  private var replacement: MLApiPlatform? = null

  private val platform: MLApiPlatform
    get() = replacement ?: IJPlatform


  override val tierDescriptors: List<TierDescriptor>
    get() = platform.tierDescriptors

  override val environmentExtenders: List<EnvironmentExtender<*>>
    get() = platform.environmentExtenders

  override val taskApproaches: List<MLTaskApproachBuilder<*>>
    get() = platform.taskApproaches


  override val taskListeners: List<MLTaskGroupListener>
    get() = platform.taskListeners

  override fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController {
    val initialPlatform = platform
    val controller = initialPlatform.addTaskListener(taskListener)
    return object : ExtensionController {
      override fun remove() {
        require(initialPlatform == platform) {
          "$taskListener should be removed within the same platform it was added in." +
          "It was added in $initialPlatform, but removed from $platform"
        }
        controller.remove()
      }
    }
  }

  override val coroutineScope: CoroutineScope
    get() = platform.coroutineScope

  override val systemLoggerBuilder: SystemLoggerBuilder
    get() = platform.systemLoggerBuilder

  fun <T> replacingWith(apiPlatform: MLApiPlatform, action: () -> T): T {
    val oldApiPlatform = replacement
    return try {
      replacement = apiPlatform
      action()
    }
    finally {
      replacement = oldApiPlatform
    }
  }
}

@ApiStatus.Internal
fun MLApiPlatform.addTaskListener(taskListener: MLTaskGroupListener, parentDisposable: Disposable) {
  val controller = addTaskListener(taskListener)
  parentDisposable.whenDisposed {
    controller.remove()
  }
}
