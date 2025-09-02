// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin
import com.intellij.util.indexing.roots.origin.ModuleRootOriginImpl
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex.Companion.getInstance
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx

internal class ModuleFilesIteratorImpl(
  private val module: Module,
  private val root: VirtualFile,
  private val recursive: Boolean,
  private val printRootsInDebugName: Boolean,
) : ModuleIndexableFilesIterator {


  override fun getDebugName(): String =
    if (printRootsInDebugName) {
      "Module '" + module.name + "' (${root.name})"
    }
    else {
      "Module '${module.name}'"
    }

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

  override fun getOrigin(): ModuleRootOrigin {
    return if (recursive) {
      ModuleRootOriginImpl(module, listOf(root), emptyList())
    }
    else {
      ModuleRootOriginImpl(module, emptyList(), listOf(root))
    }
  }

  override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
    val processorEx = toContentIteratorEx(fileIterator)
    val myWorkspaceFileIndex = getInstance(project) as WorkspaceFileIndexEx

    return if (recursive) {
      iterateContentUnderDirectory(root, processorEx, fileFilter, myWorkspaceFileIndex)
    }
    else {
      fileFilter.accept(root) && processorEx.processFileEx(root) != TreeNodeProcessingResult.STOP
    }
  }

  fun iterateContentUnderDirectory(
    dir: VirtualFile,
    processor: ContentIteratorEx,
    customFilter: VirtualFileFilter,
    myWorkspaceFileIndex: WorkspaceFileIndexEx,
  ): Boolean {
    return myWorkspaceFileIndex.processIndexableFilesRecursively(dir, processor, customFilter) { fileSet -> !isScopeDisposed() && isInContent(fileSet) }
  }

  private fun toContentIteratorEx(processor: ContentIterator): ContentIteratorEx {
    if (processor is ContentIteratorEx) {
      return processor
    }
    return ContentIteratorEx { fileOrDir: VirtualFile -> if (processor.processFile(fileOrDir)) TreeNodeProcessingResult.CONTINUE else TreeNodeProcessingResult.STOP }
  }

  fun isScopeDisposed(): Boolean {
    return module.isDisposed()
  }

  fun isInContent(fileSet: WorkspaceFileSetWithCustomData<*>): Boolean {
    val data = fileSet.data
    return data is ModuleRelatedRootData && data.module == module
  }

  override fun getRootUrls(project: Project): Set<String> = module.rootManager.contentRootUrls.toSet()
}