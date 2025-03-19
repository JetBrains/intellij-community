// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider

internal sealed interface FileViewProviderCache {
  fun get(file: VirtualFile, context: CodeInsightContext): FileViewProvider?

  fun cacheOrGet(file: VirtualFile, context: CodeInsightContext, provider: FileViewProvider): FileViewProvider

  fun getAllProviders(vFile: VirtualFile): List<FileViewProvider>

  fun remove(file: VirtualFile): Iterable<FileViewProvider>?

  /**
   * Removes cached value for ([file], [context]) pair only if the cached value equals [viewProvider]
   */
  fun remove(file: VirtualFile, context: CodeInsightContext, viewProvider: AbstractFileViewProvider): Boolean

  /**
   * Removes all existing view providers of [vFile] and installs the only provider [viewProvider]
   */
  fun removeAllFileViewProvidersAndSet(vFile: VirtualFile, viewProvider: FileViewProvider)

  fun clear()

  fun forEachKey(block: java.util.function.Consumer<VirtualFile>)

  fun forEach(block: Consumer)

  fun processQueue()

  // todo IJPL-339 allow this call only under write lock
  fun getAllEntries(): List<Entry> {
    return buildList {
      forEach { file, context, provider ->
        add(Entry(file, context, provider))
      }
    }
  }

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

  fun interface Consumer {
    fun consume(file: VirtualFile, context: CodeInsightContext, provider: FileViewProvider)
  }
}
