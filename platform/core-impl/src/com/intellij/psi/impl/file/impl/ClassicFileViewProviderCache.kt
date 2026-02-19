// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.AtomicMapCache
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer

/**
 * Stores mapping (file -> Weak(FileViewProvider)).
 * Thread-safe.
 * Does not take [CodeInsightContext] into account.
 */
internal class ClassicFileViewProviderCache : FileViewProviderCache {

  private val cache = AtomicMapCache<VirtualFile, FileViewProvider, ConcurrentMap<VirtualFile, FileViewProvider>> {
    CollectionFactory.createConcurrentWeakValueMap()
  }

  override fun clear() {
    cache.invalidate()
  }

  override fun cacheOrGet(
    file: VirtualFile,
    context: CodeInsightContext,
    provider: FileViewProvider,
  ): FileViewProvider {
    return cache.getOrPut(file) { provider }
  }

  override fun forEachKey(block: Consumer<VirtualFile>) {
    if (!(cache.isInitialized)) return
    cache.cache.keys.forEach(block)
  }

  override fun forEach(block: FileViewProviderCache.CacheEntryConsumer) {
    if (!(cache.isInitialized)) return
    val map = cache.cache
    map.forEach { (file: VirtualFile, provider: FileViewProvider?) -> // provider might be collected
      if (provider != null) {
        block.consume(file, anyContext(), provider)
      }
    }
  }

  override fun getAllProviders(vFile: VirtualFile): List<FileViewProvider> {
    return listOfNotNull(cache[vFile])
  }

  override fun get(file: VirtualFile, context: CodeInsightContext): FileViewProvider? {
    return cache.cache[file]
  }

  override fun removeAllFileViewProvidersAndSet(vFile: VirtualFile, viewProvider: FileViewProvider) {
    cache.cache[vFile] = viewProvider
  }

  override fun remove(file: VirtualFile): Iterable<FileViewProvider>? {
    val removed = cache.cache.remove(file) ?: return null
    return listOf(removed)
  }

  override fun remove(file: VirtualFile, context: CodeInsightContext, viewProvider: AbstractFileViewProvider): Boolean {
    if (!cache.isInitialized) return false
    return this.cache.cache.remove(file, viewProvider)
  }

  override fun processQueue() {
    if (!(cache.isInitialized)) return

    // cache.cache is in fact ConcurrentWeakValueHashMap.
    // calling cache.cache.remove(unrelated-object) calls ConcurrentWeakValueHashMap#processQueue under the hood
    cache.cache.remove(NULL)
  }

  override fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext? {
    throw UnsupportedOperationException()
  }
}

private val NULL: VirtualFile = LightVirtualFile()

