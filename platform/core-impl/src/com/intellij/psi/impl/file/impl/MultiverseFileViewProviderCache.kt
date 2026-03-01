// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.AtomicMapCache
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Stores mapping (file -> FileProviderMap(context -> Weak(FileViewProvider)))
 */
internal class MultiverseFileViewProviderCache : FileViewProviderCache {

  // todo IJPL-339 we need atomic update for values of the maps???

  // todo IJPL-339 don't store map for a single item
  private val cache = AtomicMapCache<VirtualFile, FileProviderMap, FullCacheMap> {
    CollectionFactory.createConcurrentWeakValueMap()
  }

  // todo IJPL-339 do clear only under write lock
  override fun clear() {
    log.doTrace { "clear cache" }
    cache.invalidate()
  }

  // todo IJPL-339 do read only under read lock
  override fun cacheOrGet(file: VirtualFile, context: CodeInsightContext, provider: FileViewProvider): FileViewProvider {
    log.doTrace { "cacheOrGet $file $context $provider" }
    val map = getFileProviderMap(file)
    val effectiveViewProvider = map.cacheOrGet(context, provider)
    log.doTrace { "cacheOrGet finished $file $context $provider, effectiveProvider=$effectiveViewProvider" }
    return effectiveViewProvider
  }

  private fun getFileProviderMap(file: VirtualFile): FileProviderMap {
    return cache.getOrPut(file) { FileProviderMap() }
  }

  override fun forEachKey(block: Consumer<VirtualFile>) {
    if (!(cache.isInitialized)) return
    cache.cache.keys.forEach(block)
  }

  override fun forEach(block: FileViewProviderCache.CacheEntryConsumer) {
    if (!(cache.isInitialized)) return
    val map = cache.cache
    map.forEach { (file, map) ->
      map.forEach { context, provider ->
        block.consume(file, context, provider)
      }
    }
  }

  override fun getAllProviders(vFile: VirtualFile): List<FileViewProvider> {
    val map = cache[vFile] ?: return emptyList()
    return map.allProviders
  }

  override fun get(file: VirtualFile, context: CodeInsightContext): FileViewProvider? {
    val result = cache.cache[file]?.get(context)
    return result
  }

  override fun removeAllFileViewProvidersAndSet(vFile: VirtualFile, viewProvider: FileViewProvider) {
    log.doTrace { "removeAllAndSetAny $vFile $viewProvider" }
    val fileMap = getFileProviderMap(vFile)
    fileMap.removeAllAndSetAny(viewProvider)
  }

  override fun remove(file: VirtualFile): Iterable<FileViewProvider>? {
    log.doTrace { "remove $file" }
    val map = cache.cache.remove(file) ?: return null
    return map.entries.asSequence().map { it.value }.asIterable()
  }

  /**
   * Removes cached value for ([file], [context]) pair only if the cached value equals [viewProvider]
   */
  override fun remove(file: VirtualFile, context: CodeInsightContext, viewProvider: AbstractFileViewProvider): Boolean {
    log.doTrace { "remove $file $context $viewProvider" }
    val result = cache.isInitialized && cache.cache[file]?.remove(context, viewProvider) == true
    log.doTrace { "remove finished $file $context $viewProvider, result=$result" }
    return result
  }

  override fun processQueue() {
    if (!(cache.isInitialized)) return

    // cache.cache is in fact ConcurrentWeakValueHashMap.
    // calling cache.cache.remove(unrelated-object) calls ConcurrentWeakValueHashMap#processQueue under the hood
    cache.cache.remove(NullFile)
  }

  override fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext? {
    log.doTrace { "trySetContext $viewProvider $context" }
    val vFile = viewProvider.virtualFile
    val map = getFileProviderMap(vFile)
    val effectiveContext = map.trySetContext(viewProvider, context)
    log.doTrace { "trySetContext finished $viewProvider $context, effectiveContext=$effectiveContext" }
    return effectiveContext
  }
}

private inline fun Logger.doTrace(block: () -> String) {
  if (!isTraceEnabled) return

  val message = block()
  if (stacktraceOnTraceLevelEnabled.get()) {
    trace(Throwable(message))
  }
  else {
    trace(message)
  }
}

private val stacktraceOnTraceLevelEnabled = AtomicBoolean(false)

@ApiStatus.Internal
object MultiverseFileViewProviderCacheLog {
  /**
   * Works only if [log] level is set to `TRACE`.
   * Use only when really necessary because it is rather expensive.
   */
  @JvmStatic
  fun enableStacktraceOnTraceLevel(disposable: Disposable) {
    val prevValue = stacktraceOnTraceLevelEnabled.getAndSet(true)
    if (prevValue) return // no need to revert value after use

    Disposer.register(disposable) {
      stacktraceOnTraceLevelEnabled.set(false)
    }
  }
}

private object NullFile : LightVirtualFile()

private typealias FullCacheMap = ConcurrentMap<VirtualFile, FileProviderMap>

private val log = logger<MultiverseFileViewProviderCache>()