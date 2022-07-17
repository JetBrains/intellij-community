// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.SyntheticLibraryOrigin
import com.intellij.util.indexing.roots.origin.SyntheticLibraryOriginImpl

internal class SyntheticLibraryIndexableFilesIteratorImpl(private val syntheticLibrary: SyntheticLibrary) : SyntheticLibraryIndexableFilesIterator {

  private fun getName() = (syntheticLibrary as? ItemPresentation)?.presentableText

  override fun getDebugName() = getName().takeUnless { it.isNullOrEmpty() }?.let { "Synthetic library '$it'" }
                                ?: syntheticLibrary.toString()

  override fun getIndexingProgressText(): String {
    val name = getName()
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.indexing.named.provider", name)
    }
    return IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies")
  }

  override fun getRootsScanningProgressText(): String {
    val name = getName()
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.library.name", name)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun getOrigin(): SyntheticLibraryOrigin = SyntheticLibraryOriginImpl(syntheticLibrary)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val roots = runReadAction { syntheticLibrary.allRoots }
    return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return syntheticLibrary.allRoots.map { it.url }.toSet()
  }
}