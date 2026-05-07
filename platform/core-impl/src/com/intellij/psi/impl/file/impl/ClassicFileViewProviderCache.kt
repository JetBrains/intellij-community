// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.defaultContext
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
internal class ClassicFileViewProviderCache(
  val newFileViewProviderFactory: NewFileViewProviderFactory,
) : FileViewProviderCache {

  private val cache = AtomicMapCache<VirtualFile, FileViewProvider> {
    CollectionFactory.createConcurrentWeakValueMap()
  }

  private val myTempProviderStorage = createTemporaryProviderStorage()
  private val evaluator: ValidityEvaluator = ValidityEvaluatorImpl(myTempProviderStorage, this, newFileViewProviderFactory)

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
    val map = cache.getCacheIfInitialized() ?: return
    map.keys.forEach(block)
  }

  override fun forEach(block: FileViewProviderCache.CacheEntryConsumer) {
    val map = cache.getCacheIfInitialized() ?: return
    map.forEach { (file: VirtualFile, provider: FileViewProvider?) -> // provider might be collected
      if (provider != null) {
        block.consume(file, anyContext(), provider)
      }
    }
  }

  override fun getAllProvidersRaw(vFile: VirtualFile): List<FileViewProvider> {
    return listOfNotNull(cache[vFile])
  }

  override fun getRaw(file: VirtualFile, context: CodeInsightContext): FileViewProvider? {
    return cache.getCacheIfInitialized()?.get(file)
  }

  override fun getAndReanimateIfNecessary(
    vFile: VirtualFile,
    context: CodeInsightContext,
  ): FileViewProvider? {
    val provider = getRaw(vFile, context) ?: return null
    return evaluator.reanimateProviderIfNecessary(vFile, provider)
  }

  override fun getAllProvidersAndReanimateIfNecessary(vFile: VirtualFile): List<FileViewProvider> {
    return listOfNotNull(getAndReanimateIfNecessary(vFile, defaultContext()))
  }

  override fun removeAllFileViewProvidersAndSet(vFile: VirtualFile, viewProvider: FileViewProvider) {
    cache[vFile] = viewProvider
  }

  override fun remove(file: VirtualFile): Iterable<FileViewProvider>? {
    val removed = cache.getCacheIfInitialized()?.remove(file) ?: return null
    return listOf(removed)
  }

  override fun remove(file: VirtualFile, context: CodeInsightContext, viewProvider: AbstractFileViewProvider): Boolean {
    return this.cache.getCacheIfInitialized()?.remove(file, viewProvider) ?: false
  }

  override fun processQueue() {
    val map = cache.getCacheIfInitialized() ?: return

    // cache.cache is in fact ConcurrentWeakValueHashMap.
    // calling cache.cache.remove(unrelated-object) calls ConcurrentWeakValueHashMap#processQueue under the hood
    map.remove(NULL)
  }

  override fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext? {
    throw UnsupportedOperationException()
  }

  override fun markPossiblyInvalidated() {
    forEach { _, _, provider ->
      provider.markPossiblyInvalidated()
    }
  }

  override fun findViewProvider(
    vFile: VirtualFile,
    context: CodeInsightContext,
  ): FileViewProvider {
    getAndReanimateIfNecessary(vFile, context)?.let {
      return it
    }

    myTempProviderStorage.get(vFile)?.let {
      return it
    }

    val viewProvider = newFileViewProviderFactory.createNewFileViewProvider(vFile, context)

    return cacheOrGet(vFile, context, viewProvider)
  }

  override fun evaluateValidity(viewProvider: AbstractFileViewProvider): Boolean {
    return evaluator.evaluateValidity(viewProvider)
  }
}

private val NULL: VirtualFile = LightVirtualFile()

