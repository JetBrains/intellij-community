// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.AtomicMapCache
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer

/**
 * Stores mapping (file -> FileProviderMap(context -> Weak(FileViewProvider)))
 */
internal class MultiverseFileViewProviderCache : FileViewProviderCache {

  // todo ijpl-339 we need atomic update for values of the maps???

  // todo ijpl-339 don't store map for a single item
  private val cache = AtomicMapCache<VirtualFile, FileProviderMap, FullCacheMap> {
    CollectionFactory.createConcurrentWeakValueMap()
  }

  // todo ijpl-339 do clear only under write lock
  override fun clear() {
    cache.invalidate()
  }

  // todo ijpl-339 do read only under read lock
  override fun cacheOrGet(file: VirtualFile, context: CodeInsightContext, provider: FileViewProvider): FileViewProvider {
    val map = getFileProviderMap(file)
    return map.cacheOrGet(context, provider)
  }

  private fun getFileProviderMap(file: VirtualFile): FileProviderMap {
    return cache.getOrPut(file) { FileProviderMap() }
  }

  override fun forEachKey(block: Consumer<VirtualFile>) {
    if (!(cache.isInitialized)) return
    cache.cache.keys.forEach(block)
  }

  override fun forEach(block: FileViewProviderCache.Consumer) {
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
    return cache.cache[file]?.get(context)
  }

  override fun removeAllFileViewProvidersAndSet(vFile: VirtualFile, viewProvider: FileViewProvider) {
    val fileMap = getFileProviderMap(vFile)
    fileMap.removeAllAndSetAny(viewProvider)
  }

  override fun remove(file: VirtualFile): Iterable<FileViewProvider>? {
    val map = cache.cache.remove(file) ?: return null
    return map.entries.asSequence().map { it.value }.asIterable()
  }

  /**
   * Removes cached value for ([file], [context]) pair only if the cached value equals [viewProvider]
   */
  override fun remove(file: VirtualFile, context: CodeInsightContext, viewProvider: AbstractFileViewProvider): Boolean {
    if (!cache.isInitialized) return false
    val map = this.cache.cache[file] ?: return false
    return map.remove(context, viewProvider)
  }

  override fun processQueue() {
    if (!(cache.isInitialized)) return

    // cache.cache is in fact ConcurrentWeakValueHashMap.
    // calling cache.cache.remove(unrelated-object) calls ConcurrentWeakValueHashMap#processQueue under the hood
    cache.cache.remove(NULL)
  }

  override fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext? {
    val vFile = viewProvider.virtualFile
    val map = getFileProviderMap(vFile)
    return map.trySetContext(viewProvider, context)
  }
}

internal data class Entry(
  val file: VirtualFile,
  val context: CodeInsightContext,
  val provider: FileViewProvider,
)

private val NULL: VirtualFile = LightVirtualFile()

private typealias FullCacheMap = ConcurrentMap<VirtualFile, FileProviderMap>

