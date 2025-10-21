// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider

/**
 * Storage for mapping from ([VirtualFile], [CodeInsightContext]) to [FileViewProvider]s.
 * Thread-safe.
 * Stored FileViewProviders can be collected by GC at any time.
 * There are two implementations: [ClassicFileViewProviderCache] and [MultiverseFileViewProviderCache].
 *
 * [ClassicFileViewProviderCache] does not take [CodeInsightContext] into account.
 * [MultiverseFileViewProviderCache] stores [FileViewProvider] for each [CodeInsightContext].
 */
internal sealed interface FileViewProviderCache {

  /**
   * Returns the cached view provider for given [file] and [context]. Can be null if the cache does not contain the entry.
   *
   * @param file The virtual file for which the view provider is being retrieved or cached.
   * @param context The code insight context associated with the requested view provider.
   *
   * @return The cached view provider for the given [file] and [context].
   */
  fun get(file: VirtualFile, context: CodeInsightContext): FileViewProvider?

  /**
   * Returns the cached view provider for the specified [file] and [context], or caches and returns the provided [provider]
   * if there is no existing entry for the given [file] and [context].
   *
   * @param file The virtual file for which the view provider is being retrieved or cached.
   * @param context The code insight context associated with the requested view provider.
   * @param provider The view provider to cache if no existing entry is found.
   *
   * @return The cached or newly cached view provider for the given [file] and [context].
   */
  fun cacheOrGet(file: VirtualFile, context: CodeInsightContext, provider: FileViewProvider): FileViewProvider

  /**
   * Returns all existing view providers of [vFile]. The returned collection is thread-safe and cannot be collected by GC.
   * [ClassicFileViewProviderCache] always returns a collection of sizes 1 or 0.
   * [MultiverseFileViewProviderCache] returns all existing providers for the given [vFile] which are cached for different contexts.
   */
  fun getAllProviders(vFile: VirtualFile): List<FileViewProvider>

  /**
   * Removes all existing view providers of [file] and returns them.
   * @see [getAllProviders]
   */
  fun remove(file: VirtualFile): Iterable<FileViewProvider>?

  /**
   * Removes cached value for ([file], [context]) pair only if the cached value equals [viewProvider]
   */
  fun remove(file: VirtualFile, context: CodeInsightContext, viewProvider: AbstractFileViewProvider): Boolean

  /**
   * Removes all existing view providers of [vFile] and installs the only provider [viewProvider]
   */
  fun removeAllFileViewProvidersAndSet(vFile: VirtualFile, viewProvider: FileViewProvider)

  /**
   * Removes all mappings from the cache.
   */
  fun clear()

  /**
   * Iterates over all [VirtualFile]s in the cache. Any file can be removed from the cache at any time.
   */
  fun forEachKey(block: java.util.function.Consumer<VirtualFile>)

  /**
   * Iterates over all cache entries. Any entry can be removed from the cache at any time.
   */
  fun forEach(block: CacheEntryConsumer)

  /**
   * Removes all entries which values are collected by GC.
   */
  fun processQueue()

  // todo IJPL-339 allow this call only under write lock
  fun getAllEntries(): List<Entry> {
    return buildList {
      forEach { file, context, provider ->
        add(Entry(file, context, provider))
      }
    }
  }

  /**
   * Removes all entries from the cache and adds provided [entries]. They can be collected by GC at any time after this method returns.
   */
  fun replaceAll(entries: Iterable<Entry>) {
    clear()
    for (entry in entries) {
      cacheOrGet(entry.file, entry.context, entry.provider)
    }
  }

  /**
   * Updates the context of [viewProvider] to [context] if the current context of viewProvider is anyContext.
   *
   * @return updated context of viewProvider, or `null` if viewProvider is missing in the cache.
   */
  fun trySetContext(viewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext?

  fun interface CacheEntryConsumer {
    fun consume(file: VirtualFile, context: CodeInsightContext, provider: FileViewProvider)
  }
}
