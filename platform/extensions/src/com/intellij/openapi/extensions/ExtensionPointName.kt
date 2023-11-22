// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith", "ReplaceGetOrSet")

package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.impl.AdapterWithCustomAttributes
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.computeIfAbsent
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.computeSafeIfAny
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.findFirstSafe
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.getByGroupingKey
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.getByKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.ThreeState
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 * Provides access to an [extension point](https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_extension_points.html). Instances of this class can be safely stored in static final fields.
 *
 * For project-level and module-level extension points use [ProjectExtensionPointName] instead to make it evident that corresponding
 * [AreaInstance] must be passed.
 */
class ExtensionPointName<T : Any>(name: @NonNls String) : BaseExtensionPointName<T>(name) {
  companion object {
    @JvmStatic
    fun <T : Any> create(name: @NonNls String): ExtensionPointName<T> = ExtensionPointName(name)
  }

  /**
   * Consider using [.getExtensionList].
   */
  @get:Obsolete
  val extensions: Array<T>
    get() = getPointImpl(null).extensions

  val extensionList: List<T>
    get() = getPointImpl(null).extensionList

  /**
   * Invokes the given consumer for each extension registered in this extension point. Logs exceptions thrown by the consumer.
   */
  fun forEachExtensionSafe(consumer: Consumer<in T>) {
    getPointImpl(areaInstance = null).processWithPluginDescriptor(shouldBeSorted = true) { adapter, _ ->
      consumer.accept(adapter)
    }
  }

  fun findFirstSafe(predicate: Predicate<in T>): T? {
    return findFirstSafe(predicate = predicate, sequence = getPointImpl(null).asSequence())
  }

  fun <R> computeSafeIfAny(processor: Function<T, out R>): R? {
    return computeSafeIfAny(processor = processor::apply, sequence = getPointImpl(null).asSequence())
  }

  val extensionsIfPointIsRegistered: List<T>
    get() = getExtensionsIfPointIsRegistered(null)

  fun getExtensionsIfPointIsRegistered(areaInstance: AreaInstance?): List<T> {
    @Suppress("DEPRECATION")
    val area = areaInstance?.extensionArea ?: Extensions.getRootArea()
    return area?.getExtensionPointIfRegistered<T>(name)?.extensionList ?: emptyList()
  }

  @Deprecated("Use {@code getExtensionList().stream()}", ReplaceWith("getExtensionList().stream()"), DeprecationLevel.ERROR)
  fun extensions(): Stream<T> = getPointImpl(null).asSequence().asStream()

  fun hasAnyExtensions(): Boolean {
    @Suppress("DEPRECATION")
    return (Extensions.getRootArea().getExtensionPointIfRegistered<T>(name) ?: return false).size() != 0
  }

  /**
   * Do not use project-level or module-level extensions.
   */
  @Obsolete
  fun getExtensionList(areaInstance: AreaInstance?): List<T> = getPointImpl(areaInstance).extensionList

  /**
   * Consider using [ProjectExtensionPointName.getExtensions]
   */
  @Obsolete
  fun getExtensions(areaInstance: AreaInstance?): Array<T> = getPointImpl(areaInstance).extensions

  @Deprecated("Do not use project-level or module-level extensions.")
  fun getPoint(areaInstance: AreaInstance?): ExtensionPoint<T> = getPointImpl(areaInstance)

  val point: ExtensionPoint<T>
    get() = getPointImpl(null)

  fun <V : T> findExtension(instanceOf: Class<V>): V? {
    return getPointImpl(null).findExtension(aClass = instanceOf, isRequired = false, strictMatch = ThreeState.UNSURE)
  }

  fun <V : T> findExtensionOrFail(exactClass: Class<V>): V {
    return getPointImpl(null).findExtension(aClass = exactClass, isRequired = true, strictMatch = ThreeState.UNSURE)!!
  }

  fun <V : T> findFirstAssignableExtension(instanceOf: Class<V>): V? {
    return getPointImpl(null).findExtension(aClass = instanceOf, isRequired = true, strictMatch = ThreeState.NO)
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behavior is not predictable -
   * events will be fired during iteration, and probably it will be not expected.
   *
   * Use only for interface extension points, not for bean.
   *
   * Due to internal reasons, there is no easy way to implement hasNext in a reliable manner,
   * so, `next` may return `null` (in this case stop iteration).
   *
   * Possible use cases:
   * 1. Conditional iteration (no need to create all extensions if iteration is stopped due to some condition).
   * 2. Iterated only once per application (no need to cache an extension list internally).
   */
  @Internal
  fun getIterable(): Iterable<T?> = getPointImpl(null).asSequence().asIterable()

  @Internal
  fun lazySequence(): Sequence<T> {
    return getPointImpl(null).asSequence()
  }

  @Internal
  fun processWithPluginDescriptor(consumer: (T, PluginDescriptor) -> Unit) {
    getPointImpl(null).processWithPluginDescriptor(consumer = consumer)
  }

  fun addExtensionPointListener(listener: ExtensionPointListener<T>, parentDisposable: Disposable?) {
    getPointImpl(null).addExtensionPointListener(listener = listener,
                                                 invokeForLoadedExtensions = false,
                                                 parentDisposable = parentDisposable)
  }

  fun addExtensionPointListener(coroutineScope: CoroutineScope, listener: ExtensionPointListener<T>) {
    getPointImpl(null).addExtensionPointListener(listener = listener,
                                                 invokeForLoadedExtensions = false,
                                                 coroutineScope = coroutineScope)
  }

  fun addExtensionPointListener(listener: ExtensionPointListener<T>) {
    getPointImpl(null).addExtensionPointListener(listener = listener, invokeForLoadedExtensions = false, parentDisposable = null)
  }

  fun addExtensionPointListener(areaInstance: AreaInstance, listener: ExtensionPointListener<T>) {
    getPointImpl(areaInstance).addExtensionPointListener(listener = listener, invokeForLoadedExtensions = false, parentDisposable = null)
  }

  fun removeExtensionPointListener(listener: ExtensionPointListener<T>) {
    getPointImpl(null).removeExtensionPointListener(listener)
  }

  fun addChangeListener(listener: Runnable, parentDisposable: Disposable?) {
    getPointImpl(null).addChangeListener(listener = listener, parentDisposable = parentDisposable)
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Values with the same key merge into a list. Return values by key.
   *
   * To exclude an extension from cache, return a null key.
   *
   * `cacheId` is required because it's dangerous to rely on identity of functional expressions.
   * JLS doesn't specify whether a new instance is produced or some common instance is reused for lambda expressions (see 15.27.4).
   */
  @ApiStatus.Experimental
  fun <K : Any> getByGroupingKey(key: K, cacheId: Class<*>, keyMapper: Function<T, K?>): List<T> {
    return getByGroupingKey(point = getPointImpl(null), cacheId = cacheId, key = key, keyMapper = keyMapper)
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Return value by key.
   *
   * To exclude an extension from cache, return a null key.
   */
  @ApiStatus.Experimental
  fun <K : Any> getByKey(key: K, cacheId: Class<*>, keyMapper: Function<T, K?>): T? {
    return getByKey(point = getPointImpl(null), key = key, cacheId = cacheId, keyMapper = keyMapper)
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Return value by key.
   *
   * To exclude an extension from cache, return a null key.
   */
  @ApiStatus.Experimental
  fun <K : Any, V : Any> getByKey(key: K,
                                  cacheId: Class<*>,
                                  keyMapper: Function<T, K?>,
                                  valueMapper: Function<T, V?>): V? {
    return getByKey(point = getPointImpl(null), key = key, cacheId = cacheId, keyMapper = keyMapper, valueMapper = valueMapper)
  }

  @ApiStatus.Experimental
  fun <K : Any, V : Any> computeIfAbsent(key: K, cacheId: Class<*>, valueMapper: Function<K, V>): V {
    return computeIfAbsent(point = getPointImpl(null), key = key, cacheId = cacheId, valueProducer = valueMapper)
  }

  /**
   * Cache some value per extension point.
   */
  @ApiStatus.Experimental
  fun <V : Any> computeIfAbsent(cacheId: Class<*>, valueMapper: Supplier<V>): V {
    return computeIfAbsent(getPointImpl(null), cacheId, valueMapper)
  }

  @Internal
  fun filterableLazySequence(): Sequence<LazyExtension<T>> {
    val point = getPointImpl(null)
    val adapters = point.sortedAdapters
    return LazyExtensionSequence(point = point, adapters = adapters)
  }

  @Internal
  fun findByIdOrFromInstance(id: String, idGetter: (T) -> String?): T? {
    val point = point as ExtensionPointImpl<T>
    point.sortedAdapters.firstOrNull { it.orderId == id }?.let { adapter ->
      return adapter.createInstance(point.componentManager)
    }

    // check only adapters without id
    for (adapter in point.sortedAdapters) {
      if (adapter.orderId == null) {
        val instance = adapter.createInstance<T>(point.componentManager) ?: continue
        if (idGetter(instance) == id) {
          return instance
        }
      }
    }
    return null
  }
}

@Internal
sealed interface LazyExtension<T> {
  val id: String?
  val instance: T?

  val implementationClassName: String
  val implementationClass: Class<T>?

  val pluginDescriptor: PluginDescriptor

  @get:Internal
  val order: LoadingOrder

  fun getCustomAttribute(name: String): String?
}

@Internal
suspend fun <T: Any, R : Any> LazyExtension<T>.useOrLogError(task: suspend (instance: T) -> R): R? {
  this as LazyExtensionImpl<T>
  try {
    return task(adapter.createInstance(point.componentManager) ?: return null)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    thisLogger().error(point.componentManager.createError(e, pluginDescriptor.pluginId))
    return null
  }
}

private class LazyExtensionSequence<T : Any>(
  private val point: ExtensionPointImpl<T>,
  private val adapters: List<ExtensionComponentAdapter>,
) : Sequence<LazyExtension<T>> {
  override fun iterator(): Iterator<LazyExtension<T>> {
    return object : Iterator<LazyExtension<T>> {
      private var currentIndex = 0

      override fun hasNext(): Boolean = currentIndex < adapters.size

      override fun next(): LazyExtension<T> = LazyExtensionImpl(adapter = adapters.get(currentIndex++), point = point)
    }
  }
}

private class LazyExtensionImpl<T : Any>(
  @JvmField val adapter: ExtensionComponentAdapter,
  @JvmField val point: ExtensionPointImpl<T>,
) : LazyExtension<T> {
  override val id: String?
    get() = adapter.orderId

  override val order: LoadingOrder
    get() = adapter.order

  override fun getCustomAttribute(name: String): String? {
    return if (adapter is AdapterWithCustomAttributes) adapter.customAttributes.get(name) else null
  }

  override val instance: T?
    get() = createOrError(adapter = adapter, point = point)

  override val implementationClassName: String
    get() = adapter.assignableToClassName

  override val implementationClass: Class<T>?
    get() {
      try {
        return adapter.getImplementationClass(point.componentManager)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        logger<ExtensionPointName<T>>().error(point.componentManager.createError(e, adapter.pluginDescriptor.pluginId))
        return null
      }
    }

  override val pluginDescriptor: PluginDescriptor
    get() = adapter.pluginDescriptor
}

private fun <T : Any> createOrError(adapter: ExtensionComponentAdapter, point: ExtensionPointImpl<T>): T? {
  try {
    return adapter.createInstance(point.componentManager)
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: Throwable) {
    logger<ExtensionPointName<T>>().error(point.componentManager.createError(e, adapter.pluginDescriptor.pluginId))
    return null
  }
}