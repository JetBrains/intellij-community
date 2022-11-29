// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.SyntheticLibraryOrigin
import com.intellij.util.indexing.roots.origin.SyntheticLibraryOriginImpl

internal class SyntheticLibraryIndexableFilesIteratorImpl(private val name: String?,
                                                          private val syntheticLibrary: SyntheticLibrary,
                                                          private val rootsToIndex: Collection<VirtualFile>) : SyntheticLibraryIndexableFilesIterator {
  constructor(syntheticLibrary: SyntheticLibrary) : this(getName(syntheticLibrary), syntheticLibrary, syntheticLibrary.allRoots)

  init {
    assert(!IndexableFilesIndex.isIntegrationFullyEnabled()) { "Shouldn't be created with IndexableFilesIndex enabled" }
  }

  override fun getDebugName() = name.takeUnless { it.isNullOrEmpty() }?.let { "Synthetic library '$it'" }
                                ?: syntheticLibrary.toString()

  override fun getIndexingProgressText(): String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.indexing.named.provider", name)
    }
    return IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies")
  }

  override fun getRootsScanningProgressText(): String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.library.name", name)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun getOrigin(): SyntheticLibraryOrigin = SyntheticLibraryOriginImpl(syntheticLibrary, rootsToIndex)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    return IndexableFilesIterationMethods.iterateRoots(project, rootsToIndex, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return rootsToIndex.map { it.url }.toSet()
  }

  companion object {
    private fun getName(syntheticLibrary: SyntheticLibrary) = (syntheticLibrary as? ItemPresentation)?.presentableText
  }
}