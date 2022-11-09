// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith", "ReplaceGetOrSet")

package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.computeIfAbsent
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.computeSafeIfAny
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.findFirstSafe
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.forEachExtensionSafe
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.getByGroupingKey
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.getByKey
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException
import java.util.function.*
import java.util.function.Function
import java.util.stream.Stream

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
  val extensions: Array<T>
    get() = getPointImpl(null).extensions

  val extensionList: List<T>
    get() = getPointImpl(null).extensionList

  /**
   * Invokes the given consumer for each extension registered in this extension point. Logs exceptions thrown by the consumer.
   */
  fun forEachExtensionSafe(consumer: Consumer<in T>) {
    forEachExtensionSafe(getPointImpl(null), consumer)
  }

  fun findFirstSafe(predicate: Predicate<in T>): T? {
    return findFirstSafe(predicate, getPointImpl(null))
  }

  fun <R> computeSafeIfAny(processor: Function<in T, out R?>): R? {
    return computeSafeIfAny(processor, getPointImpl(null))
  }

  val extensionsIfPointIsRegistered: List<T>
    get() = getExtensionsIfPointIsRegistered(null)

  fun getExtensionsIfPointIsRegistered(areaInstance: AreaInstance?): List<T> {
    @Suppress("DEPRECATION")
    val area = areaInstance?.extensionArea ?: Extensions.getRootArea()
    return area?.getExtensionPointIfRegistered<T>(name)?.extensionList ?: emptyList()
  }

  @Deprecated("Use {@code getExtensionList().stream()}", ReplaceWith("getExtensionList().stream()"))
  fun extensions(): Stream<T> {
    return getPointImpl(null).extensions()
  }

  fun hasAnyExtensions(): Boolean = getPointImpl(null).size() != 0

  /**
   * Consider using [ProjectExtensionPointName.getExtensions]
   */
  fun getExtensionList(areaInstance: AreaInstance?): List<T> = getPointImpl(areaInstance).extensionList

  /**
   * Consider using [ProjectExtensionPointName.getExtensions]
   */
  fun getExtensions(areaInstance: AreaInstance?): Array<T> = getPointImpl(areaInstance).extensions

  @Deprecated("Use app-level app extension point.")
  fun extensions(areaInstance: AreaInstance?): Stream<T> {
    return getPointImpl(areaInstance).extensionList.stream()
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("""use {@link #getPoint()} to access application-level extensions and {@link ProjectExtensionPointName#getPoint(AreaInstance)}
    to access project-level and module-level extensions""")
  fun getPoint(areaInstance: AreaInstance?): ExtensionPoint<T> = getPointImpl(areaInstance)

  val point: ExtensionPoint<T>
    get() = getPointImpl(null)

  fun <V : T> findExtension(instanceOf: Class<V>): V? {
    return getPointImpl(null).findExtension(instanceOf, false, ThreeState.UNSURE)
  }

  @ApiStatus.Internal
  fun <V> findExtensions(instanceOf: Class<V>): List<T> {
    return getPointImpl(null).findExtensions(instanceOf)
  }

  fun <V : T> findExtensionOrFail(exactClass: Class<V>): V {
    return getPointImpl(null).findExtension(exactClass, true, ThreeState.UNSURE)!!
  }

  fun <V : T> findFirstAssignableExtension(instanceOf: Class<V>): V? {
    return getPointImpl(null).findExtension(instanceOf, true, ThreeState.NO)
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behaviour is not predictable -
   * events will be fired during iteration, and probably it will be not expected.
   *
   * Use only for interface extension points, not for bean.
   *
   * Due to internal reasons, there is no easy way to implement hasNext in a reliable manner,
   * so, `next` may return `null` (in this case stop iteration).
   *
   * Possible use cases:
   * 1. Conditional iteration (no need to create all extensions if iteration will be stopped due to some condition).
   * 2. Iterated only once per application (no need to cache extension list internally).
   */
  @ApiStatus.Internal
  fun getIterable(): Iterable<T?> = getPointImpl(null)

  @ApiStatus.Internal
  fun lazySequence(): Sequence<T> {
    return getPointImpl(null).iterator().asSequence().filterNotNull()
  }

  @ApiStatus.Internal
  fun processWithPluginDescriptor(consumer: BiConsumer<in T, in PluginDescriptor>) {
    getPointImpl(null).processWithPluginDescriptor( /* shouldBeSorted = */true, consumer)
  }

  fun addExtensionPointListener(listener: ExtensionPointListener<T>, parentDisposable: Disposable?) {
    getPointImpl(null).addExtensionPointListener(listener, false, parentDisposable)
  }

  fun addExtensionPointListener(listener: ExtensionPointListener<T>) {
    getPointImpl(null).addExtensionPointListener(listener, false, null)
  }

  fun addExtensionPointListener(areaInstance: AreaInstance, listener: ExtensionPointListener<T>) {
    getPointImpl(areaInstance).addExtensionPointListener(listener, false, null)
  }

  fun removeExtensionPointListener(listener: ExtensionPointListener<T>) {
    getPointImpl(null).removeExtensionPointListener(listener)
  }

  fun addChangeListener(listener: Runnable, parentDisposable: Disposable?) {
    getPointImpl(null).addChangeListener(listener, parentDisposable)
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Values with the same key merge into list. Return values by key.
   *
   * To exclude extension from cache, return null key.
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
   * To exclude extension from cache, return null key.
   */
  @ApiStatus.Experimental
  fun <K : Any> getByKey(key: K, cacheId: Class<*>, keyMapper: Function<T, K?>): T? {
    return getByKey(getPointImpl(null), key, cacheId, keyMapper)
  }

  /**
   * Build cache by arbitrary key using provided key to value mapper. Return value by key.
   *
   * To exclude extension from cache, return null key.
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

  @ApiStatus.Internal
  interface LazyExtension<T> {
    val id: String?
    val instance: T?

    val implementationClassName: String

    val pluginDescriptor: PluginDescriptor
  }

  @ApiStatus.Internal
  fun filterableLazySequence(): Sequence<LazyExtension<T>> {
    val point = getPointImpl(null)
    val adapters = point.sortedAdapters
    return LazyExtensionSequence(point = point, adapters = adapters)
  }

  private class LazyExtensionSequence<T : Any>(
    private val point: ExtensionPointImpl<T>,
    private val adapters: List<ExtensionComponentAdapter>,
  ) : Sequence<LazyExtension<T>> {
    override fun iterator(): Iterator<LazyExtension<T>> {
      return object : Iterator<LazyExtension<T>> {
        private var currentIndex = 0

        override fun next(): LazyExtension<T> {
          val adapter = adapters.get(currentIndex++)
          return object : LazyExtension<T> {
            override val id: String?
              get() = adapter.orderId

            override val instance: T?
              get() = createOrError(adapter = adapter, point = point)
            override val implementationClassName: String
              get() = adapter.assignableToClassName

            override val pluginDescriptor: PluginDescriptor
              get() = adapter.pluginDescriptor
          }
        }

        override fun hasNext(): Boolean = currentIndex < adapters.size
      }
    }
  }

  @ApiStatus.Internal
  inline fun processExtensions(consumer: (extension: T, pluginDescriptor: PluginDescriptor) -> Unit) {
    val point = getPointImpl(null)
    for (adapter in point.sortedAdapters) {
      val extension = createOrError(adapter, point) ?: continue
      consumer(extension, adapter.pluginDescriptor)
    }
  }
}

@PublishedApi
internal fun <T : Any> createOrError(adapter: ExtensionComponentAdapter, point: ExtensionPointImpl<T>): T? {
  try {
    return adapter.createInstance(point.componentManager)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    logger<ExtensionPointName<T>>().error(point.componentManager.createError(e, adapter.pluginDescriptor.pluginId))
    return null
  }
}