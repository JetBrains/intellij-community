// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.IndexableSetContributorOriginImpl

internal class IndexableSetContributorFilesIterator(private val name: String?,
                                                    private val debugName: String,
                                                    private val projectAware: Boolean,
                                                    private val roots: Set<VirtualFile>,
                                                    private val indexableSetContributor: IndexableSetContributor) : IndexableFilesIterator {

  constructor(indexableSetContributor: IndexableSetContributor) :
    this(getName(indexableSetContributor), getDebugName(indexableSetContributor), false,
         indexableSetContributor.additionalRootsToIndex, indexableSetContributor)

  constructor(indexableSetContributor: IndexableSetContributor, project: Project) :
    this(getName(indexableSetContributor), getDebugName(indexableSetContributor), true,
         indexableSetContributor.getAdditionalProjectRootsToIndex(project), indexableSetContributor)

  override fun getDebugName(): String {
    return "Indexable set contributor '$debugName' ${if (projectAware) "(project)" else "(non-project)"}"
  }

  override fun getIndexingProgressText(): String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.indexing.named.provider", name)
    }
    return IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies")
  }

  override fun getRootsScanningProgressText(): String {
    if (!name.isNullOrEmpty()) {
      return IndexingBundle.message("indexable.files.provider.scanning.files.contributor", name)
    }
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun getOrigin(): IndexableSetOrigin = IndexableSetContributorOriginImpl(indexableSetContributor, roots)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter, excludeNonProjectRoots = false)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return roots.map { it.url }.toSet()
  }

  companion object {
    private fun getName(indexableSetContributor: IndexableSetContributor) = (indexableSetContributor as? ItemPresentation)?.presentableText
    private fun getDebugName(indexableSetContributor: IndexableSetContributor): String =
      getName(indexableSetContributor)?.takeUnless { it.isEmpty() } ?: indexableSetContributor.debugName
  }
}