// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.ContentModuleUnawareEntityOriginImpl
import com.intellij.workspaceModel.storage.EntityReference

internal class ModuleUnawareContentEntityIteratorImpl(private val reference: EntityReference<*>,
                                                      private val roots: Collection<VirtualFile>) : IndexableFilesIterator {
  override fun getDebugName(): String {
    return "Content roots from entity (${ExternalEntityIndexableIteratorImpl.getRootsDebugStr(roots)})"
  }

  override fun getIndexingProgressText(): String {
    return IndexingBundle.message("indexable.files.provider.indexing.content")
  }

  override fun getRootsScanningProgressText(): String {
    return IndexingBundle.message("indexable.files.provider.scanning.content")
  }

  override fun getOrigin(): IndexableSetOrigin = ContentModuleUnawareEntityOriginImpl(reference, roots)

  override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
    return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return roots.map { it.url }.toSet()
  }
}