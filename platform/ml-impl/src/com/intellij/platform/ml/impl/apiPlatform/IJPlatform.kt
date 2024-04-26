// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.apiPlatform

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.platform.ml.EnvironmentExtender
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.ObsoleteTierDescriptor
import com.intellij.platform.ml.TierDescriptor
import com.intellij.platform.ml.impl.MLTaskApproachBuilder
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform.replacingWith
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
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
    get() = TierDescriptor.EP_NAME.extensionList

  override val environmentExtenders: List<EnvironmentExtender<*>>
    get() = EnvironmentExtender.EP_NAME.extensionList

  override val taskApproaches: List<MLTaskApproachBuilder<*>>
    get() = MLTaskApproachBuilder.EP_NAME.extensionList

  override val taskListeners: List<MLTaskGroupListener>
    get() = MessagingProvider.collect(MLTaskGroupListenerProvider.TOPIC)

  override fun addTaskListener(taskListener: MLTaskGroupListener, parentDisposable: Disposable) {
    val connection = application.messageBus.connect(parentDisposable)
    connection.subscribe(MLTaskGroupListenerProvider.TOPIC, MLTaskGroupListenerProvider { it(taskListener) })
  }

  override fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>) {
    val printer = CodeLikePrinter()
    val codeLikeMissingDeclaration = printer.printCodeLikeString(nonDeclaredFeatures.map { it.declaration })
    thisLogger().debug("${descriptor::class.java} is missing declaration: setOf($codeLikeMissingDeclaration)")
  }

  override val coroutineScope: CoroutineScope
    get() = service<MLApiPlatformComputations>().coroutineScope
}

@Service
private class MLApiPlatformComputations(val coroutineScope: CoroutineScope)

/**
 * Also a "real-life" [MLApiPlatform], but it can be replaced with another one any time.
 *
 * We always want to test [com.intellij.platform.ml.impl.MLTaskApproach]es.
 * But after they are initialized by [com.intellij.platform.ml.impl.MLTaskApproachBuilder],
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

  override fun addTaskListener(taskListener: MLTaskGroupListener, parentDisposable: Disposable) {
    extend(taskListener, parentDisposable) { platform -> platform.addTaskListener(taskListener, parentDisposable) }
  }

  override fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>) =
    platform.manageNonDeclaredFeatures(descriptor, nonDeclaredFeatures)

  override val coroutineScope: CoroutineScope
    get() = platform.coroutineScope

  private fun <T> extend(obj: T, parentDisposable: Disposable, method: (MLApiPlatform) -> Unit) {
    val initialPlatform = platform
    method(initialPlatform)
    return parentDisposable.whenDisposed {
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
