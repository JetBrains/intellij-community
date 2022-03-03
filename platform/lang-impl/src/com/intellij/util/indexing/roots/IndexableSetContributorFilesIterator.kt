// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.origin.IndexableSetContributorOriginImpl
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin

internal class IndexableSetContributorFilesIterator(private val indexableSetContributor: IndexableSetContributor,
                                                    private val projectAware: Boolean) : IndexableFilesIterator {
  override fun getDebugName(): String {
    val debugName = getName()?.takeUnless { it.isEmpty() } ?: indexableSetContributor.debugName
    return "Indexable set contributor '$debugName' ${if (projectAware) "(project)" else "(non-project)"}"
  }

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
    val allRoots = collectRoots(project)
    return IndexableFilesIterationMethods.iterateRoots(project, allRoots, fileIterator, fileFilter, excludeNonProjectRoots = false)
  }

  private fun collectRoots(project: Project): MutableSet<VirtualFile> {
    val allRoots = runReadAction {
      if (projectAware) indexableSetContributor.getAdditionalProjectRootsToIndex(project)
      else indexableSetContributor.additionalRootsToIndex
    }
    return allRoots
  }

  override fun getRootUrls(project: Project): Set<String> {
    return collectRoots(project).map { it.url }.toSet()
  }

}