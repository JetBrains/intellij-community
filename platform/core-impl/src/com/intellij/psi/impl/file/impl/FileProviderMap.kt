// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.util.Key
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.util.containers.ContainerUtil
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Contains view providers for a given virtual file.
 */
internal sealed interface FileProviderMap {
  operator fun get(context: CodeInsightContext): FileViewProvider?

  fun removeAllAndSetAny(provider: FileViewProvider)

  fun remove(context: CodeInsightContext, provider: AbstractFileViewProvider): Boolean

  fun cacheOrGet(context: CodeInsightContext, provider: FileViewProvider): FileViewProvider

  fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext?

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
      return provider
    }

    if (context == anyContext()) {
      return findAnyContext(map)
    }

    return null
  }

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
      return defaultValue
    }

    // There was a designated default context, but its provider was collected.
    // Let's try to find another one.

    while (this.map.size() > 0) {
      // evicting collected items and assigning the new default context
      update {
        map.processQueue()
      }
      this.map.defaultValue()?.let {
        return it
      }
      // Damn it. Another view provider was collected too! Let's try one more time.
    }

    return null
  }

  override fun removeAllAndSetAny(provider: FileViewProvider) {
    update {
      newMap(anyContext(), provider)
    }
    storeStrongLinkInProvider(provider)
  }

  private fun newMap(
    context: CodeInsightContext,
    provider: FileViewProvider,
  ): ContextMap<FileViewProvider> =
    emptyContextMap<FileViewProvider>().add(context, provider)

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
        return currentProvider
      }

      if (context == anyContext()) {
        if (map.size() == 0) {
          return@update map.add(context, provider)
        }
        else {
          val defaultValue = map.defaultValue()
          if (defaultValue != null) {
            return defaultValue
          }
          else {
            // GC collected the default value, let's clean up map (processQueue()) and try again
            update {
              it.processQueue()
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

  private fun installContext(viewProvider: FileViewProvider, context: CodeInsightContext) {
    val manager = CodeInsightContextManagerImpl.getInstanceImpl(viewProvider.manager.project)
    manager.setCodeInsightContext(viewProvider, context)
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
   * If [block] returns the same instance, the update succeeds
   * If [block] returns null, updates starts from scratch
   * If [block] returns a new value, update tries doing CAS and if it does not succeed, retries from scratch.
   */
  private inline fun update(
    block: (currentMap: ContextMap<FileViewProvider>) -> ContextMap<FileViewProvider>?
  ) {
    while (true) {
      val currentMap = map
      val newMap = block(currentMap) ?: continue
      if (newMap === currentMap) {
        return
      }
      if (compareAndSet(currentMap, newMap)) {
        return
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
}

private interface ContextMap<V : Any> {
  operator fun get(key: CodeInsightContext): V?
  fun add(key: CodeInsightContext, value: V): ContextMap<V>
  fun remove(key: CodeInsightContext): ContextMap<V>
  fun entries(): Collection<Map.Entry<CodeInsightContext, V>>
  fun size(): Int
  fun defaultValue(): V? // can be null if the value was collected
  fun processQueue(): ContextMap<V>
}

@Suppress("UNCHECKED_CAST")
private fun <V : Any> emptyContextMap(): ContextMap<V> = EmptyMap as ContextMap<V>

private object EmptyMap: ContextMap<Any> {
  override fun get(key: CodeInsightContext): Any? = null
  override fun add(key: CodeInsightContext, value: Any): ContextMap<Any> = OneItemMap(key, value)
  override fun remove(key: CodeInsightContext): ContextMap<Any> = this
  override fun entries(): Collection<Map.Entry<CodeInsightContext, Any>> = emptyList()
  override fun size(): Int = 0
  override fun defaultValue(): Any? = null
  override fun processQueue(): EmptyMap = this
}

private class OneItemMap<V : Any> private constructor(
  val key: CodeInsightContext,
  val value: WeakReference<V>
): ContextMap<V> {
  constructor(key: CodeInsightContext, value: V): this(key, WeakReference(value))

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
): ContextMap<V> {

  override fun get(key: CodeInsightContext): V? = map[key]

  override fun add(key: CodeInsightContext, value: V): ContextMap<V> {
    var newMap: MutableMap<CodeInsightContext, V>? = null

    for (k in map.keys) {
      val v = map[k]
      if (v != null) {
        if (newMap == null) {
          newMap = ContainerUtil.createWeakValueMap<CodeInsightContext, V>()
        }
        newMap[k] = v
      }
    }

    if (newMap == null) {
      // we have only one (k, v) pair
      return OneItemMap(key, value)
    }

    newMap[key] = value

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

    var existingKey: CodeInsightContext? = null
    var existingValue: V? = null

    for (k in map.keys) {
      if (k == key) continue
      val value = map[k]
      if (value != null) {
        if (existingKey == null) {
          existingKey = k
          existingValue = value
        }
        else {
          if (newMap == null) {
            newMap = ContainerUtil.createWeakValueMap<CodeInsightContext, V>()
          }
          newMap[existingKey] = existingValue!!
          newMap[k] = value
        }
      }
    }

    if (existingKey == null) {
      // we don't have even one survived value, GC collected all of them
      return emptyContextMap()
    }

    if (newMap == null) {
      // we have only one (k, v) pair
      return OneItemMap(existingKey, existingValue!!)
    }

    val newDefaultContext = when (key) {
      defaultContext -> newMap.keys.first() // todo IJPL-339 does changing the default context require some more care???
      else -> defaultContext
    }
    return ManyItemMap(newMap, newDefaultContext)
  }

  override fun entries(): Collection<Map.Entry<CodeInsightContext, V>> {
    return map.keys.mapNotNull { key ->
      val value = map[key] ?: return@mapNotNull null
      EntryImpl(key, value) }
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

  override fun toString(): String {
    return "ManyItemMap(map=$map)"
  }
}

private class EntryImpl<V : Any>(
  override val key: CodeInsightContext,
  override val value: V,
): Map.Entry<CodeInsightContext, V>

private val strongLinkToFileProviderMap = Key.create<FileProviderMap>("strongLinkToFileProviderMap")