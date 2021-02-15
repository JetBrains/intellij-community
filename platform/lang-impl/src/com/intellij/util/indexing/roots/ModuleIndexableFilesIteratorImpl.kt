// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ModuleFileIndexImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin
import com.intellij.util.indexing.roots.kind.ModuleRootOriginImpl

internal class ModuleIndexableFilesIteratorImpl(private val module: Module,
                                                private val root: VirtualFile) : ModuleIndexableFilesIterator {
  companion object {
    @JvmStatic
    fun getModuleIterators(module: Module): Collection<ModuleIndexableFilesIteratorImpl> {
      val fileIndex = ModuleRootManager.getInstance(module).fileIndex as ModuleFileIndexImpl
      return fileIndex.moduleRootsToIterate.map { ModuleIndexableFilesIteratorImpl(module, it) }
    }
  }

  override fun getDebugName() = "Module '${module.name}'"

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

  override fun getOrigin(): ModuleRootOrigin = ModuleRootOriginImpl(module, root)

  override fun iterateFiles(
    project: Project,
    fileIterator: ContentIterator,
    fileFilter: VirtualFileFilter
  ): Boolean = ModuleRootManager.getInstance(module).fileIndex.iterateContentUnderDirectory(root, fileIterator, fileFilter)
}