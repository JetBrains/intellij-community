// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A map that stores file view providers for a given virtual file.
 * Is thread-safe.
 * Stored [FileViewProvider]s can be collected by GC at any time.
 *
 * The map handles [anyContext] as a special case:
 * - The map can be empty.
 * - If the map contains only one entry, it can have as a key either a specific context or [anyContext].
 * - If the map contains only [anyContext] entry, and it receives a request for some specific context, the provider of [anyContext] is reassigned to the requested context, and it is returned.
 * - If the map contains several view providers, all of them are guaranteed to be assigned to some specific contexts ([anyContext] is not allowed to be stored in the map)
 * - If this map contains several entries or a single entry assigned to non-[anyContext], and it receives a request for [anyContext], one of the existing providers is returned.
 *   The returned provider is guaranteed to be the same for subsequent requests until it gets collected by GC.
 */
internal sealed interface FileProviderMap {

  /**
   * Returns a view provider for the given [context]. Can be null if there's no view provider for the given context assigned yet.
   */
  operator fun get(context: CodeInsightContext): FileViewProvider?

  /**
   * Removes all existing view providers from this and installs the only [provider] tp [anyContext]
   */
  fun removeAllAndSetAny(provider: FileViewProvider)

  /**
   * Removes the view provider for the given [context] if it is equal to [provider].
   *
   * @return true if the provider was removed.
   */
  fun remove(context: CodeInsightContext, provider: AbstractFileViewProvider): Boolean

  /**
   * Tries to cache or get [provider] for [context].
   *
   * @return the actual provider stored in the map. Either [provider] or a provider that was already stored in the map.
   */
  fun cacheOrGet(context: CodeInsightContext, provider: FileViewProvider): FileViewProvider

  /**
   * Support [anyContext] special case. See doc of [FileProviderMap].
   *
   * This method is called when the map contains a single entry, it has [anyContext] as a key, and we want to reassigning this entry to [context]..
   * The method tries to assign the context to the existing provider.
   *
   * @return the actual context assigned to [viewProvider]. Either [context] or a context that had been assigned to [viewProvider] concurrently.
   */
  fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext?

  /**
   * Returns all existing entries. The returned collection is thread-safe and cannot be collected by GC.
   * Thus, don't store it in a field.
   */
  val entries: Collection<Map.Entry<CodeInsightContext, FileViewProvider>>
}

internal fun FileProviderMap(): FileProviderMap = FileProviderMapImpl()

internal fun FileProviderMap.forEach(block: (context: CodeInsightContext, provider: FileViewProvider) -> Unit) {
  for (entry in entries) {
    block(entry.key, entry.value)
  }
}

internal val FileProviderMap.allProviders: List<FileViewProvider>
  get() = entries.map { it.value }

@ReviseWhenPortedToJDK("11") // rewrite to VarHandles to avoid smelling AtomicReference inheritance
private class FileProviderMapImpl : FileProviderMap, AtomicReference<ContextMap<FileViewProvider>>(emptyContextMap()) {

  private val map: ContextMap<FileViewProvider>
    get() = this.get()

  override fun get(context: CodeInsightContext): FileViewProvider? {
    val map = map
    val provider = map[context]

    if (provider != null) {
      log.trace { "found provider in [$this]" }
      return provider
    }

    if (context == anyContext()) {
      return findAnyContext(map)
    }

    log.trace { "no provider found in [$this]" }
    return null
  }

  /**
   * [map] has a designated value for [anyContext] that is called [ContextMap.defaultValue].
   *
   * We try to use it if it's not collected.
   * Otherwise, we process the GC queue and try again.
   */
  private fun findAnyContext(
    map: ContextMap<FileViewProvider>,
  ): FileViewProvider? {
    if (map.size() == 0) {
      return null
    }

    // The map is not empty, and we are asking for ANY view provider
    // In this case, we can return a view provider of a real context by calling `map.defaultValue`.

    val defaultValue = map.defaultValue()
    if (defaultValue != null) {
      log.trace { "anyContext found for [$this]" }
      return defaultValue
    }

    // There was a designated default context, but its provider was collected.
    // Let's try to find another one.

    var cancellationCounter = 0
    while (this.map.size() > 0) {
      // evicting collected items and assigning the new default context
      update {
        map.processQueue()
      }
      this.map.defaultValue()?.let {
        log.trace { "anyContext found for [$this] after GC queue processing." }
        return it
      }
      // Damn it. Another view provider was collected too! Let's try one more time.
      log.trace { "anyContext was GCed for [$this]. Trying again" }
      cancellationCounter++
      if (cancellationCounter % 1000 == 0) {
        log.error("Can't find anyContext by ${cancellationCounter} attempts. $this")
        ProgressManager.checkCanceled()
      }
    }

    log.trace { "anyContext was GCed for [$this]. no provider found" }
    return null
  }

  override fun removeAllAndSetAny(provider: FileViewProvider) {
    update {
      mapOf(anyContext(), provider)
    }
    storeStrongLinkInProvider(provider)
  }

  /**
   * Write-only code. Please don't try to amend.
   *
   * Generally speaking, caches-or-gets provider, but there are some intricate details:
   * - If [context] is [anyContext] and there's an existing provider (with arbitrary context) in the map, we return this provider.
   *
   * - If [context] is [anyContext] and the map is empty, we put [provider] to the map.
   *
   * - If [context] is a proper context and the map contains only a context for [anyContext], we try to reassign this existing
   *   provider to [context].
   */
  override fun cacheOrGet(context: CodeInsightContext, provider: FileViewProvider): FileViewProvider {
    update { map ->
      val currentProvider = map[context]
      if (currentProvider != null) {
        log.trace { "found provider in [$this]" }
        return currentProvider
      }

      if (context == anyContext()) {
        if (map.size() == 0) {
          return@update map.add(context, provider)
        }
        else {
          val defaultValue = map.defaultValue()
          if (defaultValue != null) {
            log.trace { "found provider for any-context in [$this]" }
            return defaultValue
          }
          else {
            // GC collected the default value, let's clean up map (processQueue()) and try again
            update { map1 ->
              map1.processQueue()
            }
            return@update null
          }
        }
      }
      else {
        val anyViewProvider = map[anyContext()]
        if (anyViewProvider != null) {
          require(map.size() == 1) {
            "AnyContext can be stored in the map only if it's the only item in the map. " +
            "Otherwise, the other context should be used as a default context."
          }

          val updatedContext = trySetContext(anyViewProvider, context)
          if (updatedContext === context) {
            log.trace { "found provider in [$this]" }
            return anyViewProvider
          }
          else {
            // anyViewProvider was concurrently updated to another context, or was removed.
            // we need to start the update from scratch
            return@update null
          }
        }
        else {
          return@update map.add(context, provider)
        }
      }
    }
    storeStrongLinkInProvider(provider)
    return provider
  }

  override fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext? {
    update { map ->
      val existingProvider = map[anyContext()]
      if (existingProvider !== viewProvider) {
        val entry = map.entries().firstOrNull { it.value === viewProvider }
        if (entry != null) {
          val concurrentlySetContext = entry.key
          // The concurrentlySetContext might not yet be installed by another thread. Let's help it.
          installContext(viewProvider, concurrentlySetContext)
          return concurrentlySetContext
        }
        else {
          // viewProvider is missing in the map. Aborting with null
          return null
        }
      }
      return@update map.remove(anyContext()).add(context, viewProvider)
    }
    installContext(viewProvider, context)
    return context
  }

  override fun remove(context: CodeInsightContext, provider: AbstractFileViewProvider): Boolean {
    update { map ->
      val currentProvider = map[context]
      if (currentProvider !== provider) {
        return false
      }
      // todo IJPL-339 support default context???
      //  how to handle case when they want to remove default context and we substitute it with another context???

      return@update map.remove(context)
    }
    return true
  }

  override val entries: Collection<Map.Entry<CodeInsightContext, FileViewProvider>>
    get() = map.entries()

  /**
   * Updates the map atomically with the provided [block].
   *
   * The [block] can run several times:
   *  - If [block] returns the same instance, the update succeeds
   *  - If [block] returns `null`, updates starts from scratch
   *  - If [block] returns a new value, update tries doing CAS and if it does not succeed, retries from scratch.
   */
  @OptIn(ExperimentalContracts::class)
  private inline fun update(
    block: (currentMap: ContextMap<FileViewProvider>) -> ContextMap<FileViewProvider>?,
  ) {
    contract {
      callsInPlace(block, kotlin.contracts.InvocationKind.AT_LEAST_ONCE)
    }

    var cancellationCounter = 0
    while (true) {
      val currentMap = map
      val newMap = block(currentMap) ?: continue
      if (newMap === currentMap) {
        return
      }
      if (compareAndSet(currentMap, newMap)) {
        return
      }

      cancellationCounter++
      if (cancellationCounter % 1000 == 0) {
        ProgressManager.checkCanceled()
      }
    }
  }

  /**
   * we need to store a strong link to FileProviderMap within a FileViewProvider
   * so that FileProviderMap is not collected before FileViewProvider
   */
  private fun storeStrongLinkInProvider(provider: FileViewProvider) {
    provider.putUserData(strongLinkToFileProviderMap, this)
  }

  override fun toString(): String = "FileProviderMapImpl(map=$map)"
}

private fun mapOf(
  context: CodeInsightContext,
  provider: FileViewProvider,
): ContextMap<FileViewProvider> =
  emptyContextMap<FileViewProvider>().add(context, provider)

private fun installContext(viewProvider: FileViewProvider, context: CodeInsightContext) {
  val manager = CodeInsightContextManagerImpl.getInstanceImpl(viewProvider.manager.project)
  manager.setCodeInsightContext(viewProvider, context)
}

/**
 * An immutable map from contexts to [V].
 * It has a stable [defaultValue] which corresponds to one of the contexts.
 * It stays the same during the whole life of a given [ContextMap]. Though it can be collected by GC.
 * To reassign the default context, [processQueue] should be called which will return a new instance of [ContextMap] with a new default context.
 */
@ApiStatus.Internal
@VisibleForTesting
interface ContextMap<V : Any> {
  operator fun get(key: CodeInsightContext): V?
  fun add(key: CodeInsightContext, value: V): ContextMap<V>
  fun remove(key: CodeInsightContext): ContextMap<V>
  fun entries(): Collection<Map.Entry<CodeInsightContext, V>>
  fun size(): Int
  fun defaultValue(): V? // can be null if the value was collected
  fun processQueue(): ContextMap<V>
}

@Suppress("UNCHECKED_CAST")
@ApiStatus.Internal
@VisibleForTesting
fun <V : Any> emptyContextMap(): ContextMap<V> = EmptyMap as ContextMap<V>

private object EmptyMap : ContextMap<Any> {
  override fun get(key: CodeInsightContext): Any? = null
  override fun add(key: CodeInsightContext, value: Any): ContextMap<Any> = OneItemMap(key, value)
  override fun remove(key: CodeInsightContext): ContextMap<Any> = this
  override fun entries(): Collection<Map.Entry<CodeInsightContext, Any>> = emptyList()
  override fun size(): Int = 0
  override fun defaultValue(): Any? = null
  override fun processQueue(): EmptyMap = this
  override fun toString(): String = "EmptyMap"
}

private class OneItemMap<V : Any> private constructor(
  val key: CodeInsightContext,
  val value: WeakReference<V>,
) : ContextMap<V> {
  constructor(key: CodeInsightContext, value: V) : this(key, WeakReference(value))

  override fun get(key: CodeInsightContext): V? {
    return if (this.key == key) value.get()
    else null
  }

  override fun add(key: CodeInsightContext, value: V): ContextMap<V> {
    return when {
      key == this.key -> OneItemMap(key, value)
      else -> {
        val key1 = this.key
        val value1 = this.value.get()
        if (value1 != null) {
          val map = ContainerUtil.createWeakValueMap<CodeInsightContext, V>()
          map[key1] = value1
          map[key] = value
          ManyItemMap(map, key1)
        }
        else {
          OneItemMap(key, value)
        }
      }
    }
  }

  override fun remove(key: CodeInsightContext): ContextMap<V> {
    return when {
      key == this.key || value.get() == null -> emptyContextMap()
      else -> this
    }
  }

  override fun entries(): Collection<Map.Entry<CodeInsightContext, V>> {
    val key = key
    val value = value.get() ?: return emptyList()
    return listOf(EntryImpl(key, value))
  }

  override fun size(): Int = 1
  override fun defaultValue(): V? = value.get()
  override fun processQueue(): ContextMap<V> {
    val v = value.get()
    return if (v == null) emptyContextMap()
    else this
  }

  override fun toString(): String {
    return "OneItemMap(key=$key, value=${value.get()})"
  }
}

private class ManyItemMap<V : Any>(
  private val map: Map<CodeInsightContext, V>,
  private val defaultContext: CodeInsightContext,
) : ContextMap<V> {

  override fun get(key: CodeInsightContext): V? = map[key]

  override fun add(key: CodeInsightContext, value: V): ContextMap<V> {
    var newMap: MutableMap<CodeInsightContext, V>? = null

    for (k in map.keys) {
      val v = map[k] ?: continue

      if (newMap == null) {
        // constructing map only if at least 2 elements are found
        newMap = newMap(key, value)
      }
      newMap[k] = v
    }

    if (newMap == null) {
      // we have only one (k, v) pair
      return OneItemMap(key, value)
    }

    val newDefaultContext = if (newMap[defaultContext] == null) {
      key // todo IJPL-339 does changing the default context require some more care???
    }
    else {
      defaultContext
    }
    return ManyItemMap(newMap, newDefaultContext)
  }

  override fun remove(key: CodeInsightContext): ContextMap<V> {
    if (map[key] == null) {
      return this
    }

    return produceNewMapWithoutKey(key)
  }

  private fun produceNewMapWithoutKey(key: CodeInsightContext?): ContextMap<V> {
    var newMap: MutableMap<CodeInsightContext, V>? = null

    var firstKey: CodeInsightContext? = null
    var firstValue: V? = null

    for (k in map.keys) {
      if (k == key) continue

      val value = map[k] ?: continue

      if (firstKey == null) {
        firstKey = k
        firstValue = value
      }
      else {
        if (newMap == null) {
          // creating new map only if at least 2 elements are found
          newMap = newMap(firstKey, firstValue!!)
        }
        newMap[k] = value
      }
    }

    if (firstKey == null) {
      // we don't have even one survived value, GC collected all of them
      return emptyContextMap()
    }

    if (newMap == null) {
      // we have only one (k, v) pair
      return OneItemMap(firstKey, firstValue!!)
    }

    val newDefaultContext = if (key == defaultContext || newMap[defaultContext] == null) {
      // todo IJPL-339 does changing the default context require some more care???
      firstKey
    }
    else {
      defaultContext
    }
    return ManyItemMap(newMap, newDefaultContext)
  }

  private fun newMap(firstKey: CodeInsightContext, firstValue: V): MutableMap<CodeInsightContext, V> {
    val map = ContainerUtil.createWeakValueMap<CodeInsightContext, V>()
    map[firstKey] = firstValue
    return map
  }

  override fun entries(): Collection<Map.Entry<CodeInsightContext, V>> {
    return map.keys.mapNotNull { key ->
      val value = map[key] ?: return@mapNotNull null
      EntryImpl(key, value)
    }
  }

  override fun size(): Int = map.size

  override fun defaultValue(): V? = map[defaultContext]

  override fun processQueue(): ContextMap<V> {
    val newMap = produceNewMapWithoutKey(null)
    if (newMap is ManyItemMap && newMap.map.size == this.map.size) {
      // no items were collected, let's keep our instance
      return this
    }
    else {
      return newMap
    }
  }

  override fun toString(): String =
    "ManyItemMap(map=" + map.entries.joinToString(separator = ", ", prefix = "{", postfix = "}") { (k, v) -> "$k=$v" } + ", defaultContext=$defaultContext)"
}

private class EntryImpl<V : Any>(
  override val key: CodeInsightContext,
  override val value: V,
) : Map.Entry<CodeInsightContext, V>

private val log = com.intellij.openapi.diagnostic.logger<FileProviderMap>()

private val strongLinkToFileProviderMap = Key.create<FileProviderMap>("strongLinkToFileProviderMap")