// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.andIndexable
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin
import com.intellij.util.indexing.roots.origin.ModuleRootOriginImpl
import com.intellij.util.indexing.unwrapCacheAvoiding
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex

internal class ModuleIndexableFilesIteratorImpl private constructor(private val module: Module) : ModuleIndexableFilesIterator {

  companion object {
    fun createIterators(module: Module): Collection<IndexableFilesIterator> {
      return listOf(ModuleIndexableFilesIteratorImpl(module))
    }
  }

  override fun getDebugName(): String = "Module '" + module.name + "' (all roots)"

  override fun getIndexingProgressText(): String =
    if (ModuleType.isInternal(module)) {
      IndexingBundle.message("indexable.files.provider.indexing.internal.module.name")
    }
    else {
      IndexingBundle.message("indexable.files.provider.indexing.module.name", module.name)
    }

  override fun getRootsScanningProgressText(): String {
    if (ModuleType.isInternal(module))
      return IndexingBundle.message("indexable.files.provider.scanning.internal.module.name")
    return IndexingBundle.message("indexable.files.provider.scanning.module.name", module.name)
  }

  override fun getOrigin(): ModuleRootOrigin = ModuleRootOriginImpl(module, null, null)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean {
    val index = runReadActionBlocking {
      if (module.isDisposed) null else ModuleRootManager.getInstance(module).fileIndex
    }

    if (index == null) return false

    val fileIterator = fileIterator.unwrapCacheAvoiding()
    val fileFilter = fileFilter.andIndexable(WorkspaceFileIndex.getInstance(project))
    return index.iterateContent(fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> = module.rootManager.contentRootUrls.toSet()
}