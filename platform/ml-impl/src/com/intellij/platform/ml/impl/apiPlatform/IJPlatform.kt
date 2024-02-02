// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.apiPlatform

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.EnvironmentExtender
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.ObsoleteTierDescriptor
import com.intellij.platform.ml.TierDescriptor
import com.intellij.platform.ml.impl.MLTaskApproachInitializer
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform.ExtensionController
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform.replacingWith
import com.intellij.platform.ml.impl.logger.MLEvent
import com.intellij.platform.ml.impl.monitoring.MLApiStartupListener
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import com.intellij.util.application
import com.intellij.util.messages.Topic
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

fun interface MLTaskListenerProvider : MessagingProvider<MLTaskGroupListener> {
  companion object {
    val TOPIC = MessagingProvider.createTopic<MLTaskGroupListener, MLTaskListenerProvider>("ml.task")
  }
}

fun interface MLEventProvider : MessagingProvider<MLEvent> {
  companion object {
    val TOPIC = MessagingProvider.createTopic<MLEvent, MLEventProvider>("ml.event")
  }
}

fun interface MLApiStartupListenerProvider : MessagingProvider<MLApiStartupListener> {
  companion object {
    val TOPIC = MessagingProvider.createTopic<MLApiStartupListener, MLApiStartupListenerProvider>("ml.startup")
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
    get() = TierDescriptor.EP_NAME.extensionList

  override val environmentExtenders: List<EnvironmentExtender<*>>
    get() = EnvironmentExtender.EP_NAME.extensionList

  override val taskApproaches: List<MLTaskApproachInitializer<*>>
    get() = MLTaskApproachInitializer.EP_NAME.extensionList

  override val taskListeners: List<MLTaskGroupListener>
    get() = MessagingProvider.collect(MLTaskListenerProvider.TOPIC)

  override val events: List<MLEvent>
    get() = MessagingProvider.collect(MLEventProvider.TOPIC)

  override val startupListeners: List<MLApiStartupListener>
    get() = MessagingProvider.collect(MLApiStartupListenerProvider.TOPIC)

  override fun addStartupListener(listener: MLApiStartupListener): ExtensionController {
    val connection = application.messageBus.connect()
    connection.subscribe(MLApiStartupListenerProvider.TOPIC, MLApiStartupListenerProvider { collector -> collector(listener) })
    return ExtensionController { connection.disconnect() }
  }

  override fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController {
    val connection = application.messageBus.connect()
    connection.subscribe(MLTaskListenerProvider.TOPIC, MLTaskListenerProvider { it(taskListener) })
    return ExtensionController { connection.disconnect() }
  }

  override fun addEvent(event: MLEvent): ExtensionController {
    val connection = application.messageBus.connect()
    connection.subscribe(MLEventProvider.TOPIC, MLEventProvider { it(event) })
    return ExtensionController { connection.disconnect() }
  }

  override fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>) {
    if (!Registry.`is`("ml.description.logMissing")) return
    val printer = CodeLikePrinter()
    val codeLikeMissingDeclaration = printer.printCodeLikeString(nonDeclaredFeatures.map { it.declaration })
    thisLogger().info("${descriptor::class.java} is missing declaration: setOf($codeLikeMissingDeclaration)")
  }
}

/**
 * Also a "real-life" [MLApiPlatform], but it can be replaced with another one any time.
 *
 * We always want to test [com.intellij.platform.ml.impl.MLTaskApproach]es.
 * But after they are initialized by [com.intellij.platform.ml.impl.MLTaskApproachInitializer],
 * the passed [MLApiPlatform] could already spread all the way within the API.
 * But the user-defined instances of the api could be overridden for testing sake.
 *
 * To replace all [TierDescriptor], [EnvironmentExtender] and [MLTaskApproachInitializer]
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

  override val taskApproaches: List<MLTaskApproachInitializer<*>>
    get() = platform.taskApproaches


  override val taskListeners: List<MLTaskGroupListener>
    get() = platform.taskListeners

  override val events: List<MLEvent>
    get() = platform.events

  override val startupListeners: List<MLApiStartupListener>
    get() = platform.startupListeners


  override fun addStartupListener(listener: MLApiStartupListener): ExtensionController {
    return extend(listener) { platform -> platform.addStartupListener(listener) }
  }

  override fun addTaskListener(taskListener: MLTaskGroupListener): ExtensionController {
    return extend(taskListener) { platform -> platform.addTaskListener(taskListener) }
  }

  override fun addEvent(event: MLEvent): ExtensionController {
    return extend(event) { platform -> platform.addEvent(event) }
  }

  override fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>) =
    platform.manageNonDeclaredFeatures(descriptor, nonDeclaredFeatures)

  private fun <T> extend(obj: T, method: (MLApiPlatform) -> ExtensionController): ExtensionController {
    val initialPlatform = platform
    method(initialPlatform)
    return ExtensionController {
      require(initialPlatform == platform) {
        "$obj should be removed within the same platform it was added in." +
        "It was added in $initialPlatform, but removed from $platform"
      }
    }
  }

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
