// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.IndexableSetContributorOriginImpl

internal class IndexableSetContributorFilesIterator(private val indexableSetContributor: IndexableSetContributor,
                                                    private val projectAware: Boolean) : IndexableFilesIterator {
  override fun getDebugName() = getName().takeUnless { it.isNullOrEmpty() }
                                  ?.let { "IndexableSetContributor ${if (projectAware) "(project)" else "(non-project)"} '$it'" }
                                ?: indexableSetContributor.toString()

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
      return IndexingBundle.message("indexable.files.provider.scanning.files.contributor", name)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun getOrigin(): IndexableSetOrigin = IndexableSetContributorOriginImpl(indexableSetContributor)

  private fun getName() = (indexableSetContributor as? ItemPresentation)?.presentableText

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val allRoots = runReadAction {
      if (projectAware) indexableSetContributor.getAdditionalProjectRootsToIndex(project)
      else indexableSetContributor.additionalRootsToIndex
    }
    return IndexableFilesIterationMethods.iterateRoots(project, allRoots, fileIterator, fileFilter, excludeNonProjectRoots = false)
  }

}