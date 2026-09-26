// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.DirectorySearchEngine
import com.intellij.find.DirectorySearchEngine.FileSearchCandidate
import com.intellij.find.FindModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import java.util.function.Consumer

/**
 * Uses weight `0` for every directory.
 * Expands exactly one level:
 *   * so the search can select another engine for each child directory
 *   * so this search engine does not need to burden with excludes by itself
 */
internal class DefaultDirectorySearchEngine : DirectorySearchEngine {
  override fun canSearch(findModel: FindModel): Boolean = true

  override fun canSearchNames(): Boolean = false

  override fun getWeight(directory: VirtualFile): Int = 0

  override fun searchDirectory(directory: VirtualFile, findModel: FindModel, consumer: Consumer<in Collection<VirtualFile>>) {
    consumer.accept(directory.children.asList())
  }

  override fun searchNames(directory: VirtualFile, pathPattern: String, consumer: Consumer<FileSearchCandidate>) {
    thisLogger().error("This searcher cannot search names at the moment")
  }
}
