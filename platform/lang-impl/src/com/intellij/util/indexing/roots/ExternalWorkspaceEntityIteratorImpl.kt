// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.ExternalEntityOriginImpl
import com.intellij.workspaceModel.storage.EntityReference

internal class ExternalWorkspaceEntityIteratorImpl(private val reference: EntityReference<*>,
                                                   private val roots: Collection<VirtualFile>,
                                                   private val sourceRoots: Collection<VirtualFile>) : IndexableFilesIterator {
  override fun getDebugName(): String {
    return "External roots from entity (${getRootsDebugStr(roots)}, ${getRootsDebugStr(sourceRoots)})"
  }

  override fun getIndexingProgressText(): String {
    return IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies")
  }

  override fun getRootsScanningProgressText(): String {
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
  }

  override fun getOrigin(): IndexableSetOrigin = ExternalEntityOriginImpl(reference, roots, sourceRoots)

  override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
    return IndexableFilesIterationMethods.iterateRoots(project, allRoots, fileIterator, fileFilter)
  }

  private val allRoots: Collection<VirtualFile>
    get() = roots + sourceRoots

  override fun getRootUrls(project: Project): Set<String> {
    return allRoots.map { it.url }.toSet()
  }

  companion object {
    fun getRootsDebugStr(files: Collection<VirtualFile>) =
      if (files.isEmpty()) "empty" else files.map { it.name }.sorted().joinToString(", ", limit = 3)
  }
}