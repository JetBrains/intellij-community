// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.IndexableFilesIterationMethods.iterateRoots
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import org.jetbrains.annotations.Nls

internal class AdditionalLibraryIndexableAddedFilesIterator(val presentableLibraryName: @Nls String?,
                                                            val rootsToIndex: Iterable<VirtualFile>,
                                                            val libraryNameForDebug: String) : IndexableFilesIterator {
  override fun getDebugName(): String = "Additional library change reindexing iterator for ${presentableLibraryName ?: "unknown"} library; $libraryNameForDebug"

  override fun getIndexingProgressText(): String = presentableLibraryName?.let {
    IndexingBundle.message("progress.text.additional.library.indexing.added.files", it)
  } ?: IndexingBundle.message("progress.text.additional.library.indexing.unknown.added.files")

  override fun getRootsScanningProgressText(): String = presentableLibraryName?.let {
    IndexingBundle.message("progress.text.additional.library.scanning.added.files", it)
  } ?: IndexingBundle.message("progress.text.additional.library.scanning.unknown.added.files")

  override fun getOrigin(): IndexableSetOrigin = PartialAdditionalLibraryIndexableSetOrigin(rootsToIndex)

  override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
    return iterateRoots(project, rootsToIndex, fileIterator, fileFilter, true)
  }

  override fun getRootUrls(project: Project): Set<String> = rootsToIndex.map { it.url }.toSet()
}

private data class PartialAdditionalLibraryIndexableSetOrigin(val rootsToIndex: Iterable<VirtualFile>) : IndexableSetOrigin