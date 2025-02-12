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
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx

internal class ModuleFilesIteratorImpl(
  private val module: Module,
  private val roots: List<VirtualFile>,
  private val nonRecursiveRoots: List<VirtualFile>,
  private val printRootsInDebugName: Boolean,
) : IndexableFilesIterator {


  override fun getDebugName(): String =
    if (printRootsInDebugName) {
      val rootsDebugStr = if (roots.isEmpty()) {
        "empty"
      }
      else {
        roots.map { it.name }.sorted().joinToString(", ", limit = 10)
      }
      "Module '" + module.name + "' ($rootsDebugStr)"
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

  override fun getOrigin(): ModuleRootOrigin = ModuleRootOriginImpl(module, roots, nonRecursiveRoots)

  override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
    val processorEx = toContentIteratorEx(fileIterator)
    val myWorkspaceFileIndex = getInstance(project) as WorkspaceFileIndexEx

    for (root in roots) {
      if (!iterateContentUnderDirectory(root, processorEx, fileFilter, myWorkspaceFileIndex)) {
        return false
      }
    }
    for (root in nonRecursiveRoots) {
      if ((fileFilter.accept(root)) && processorEx.processFileEx(root) == TreeNodeProcessingResult.STOP) {
        return false
      }
    }
    return true
  }

  fun iterateContentUnderDirectory(
    dir: VirtualFile,
    processor: ContentIteratorEx,
    customFilter: VirtualFileFilter?,
    myWorkspaceFileIndex: WorkspaceFileIndexEx,
  ): Boolean {
    return myWorkspaceFileIndex.processContentFilesRecursively(dir, processor, customFilter) { fileSet: WorkspaceFileSetWithCustomData<*> -> !isScopeDisposed() && isInContent(fileSet) }
  }

  private fun toContentIteratorEx(processor: ContentIterator): ContentIteratorEx {
    if (processor is ContentIteratorEx) {
      return processor
    }
    return ContentIteratorEx { fileOrDir: VirtualFile? -> if (processor.processFile(fileOrDir!!)) TreeNodeProcessingResult.CONTINUE else TreeNodeProcessingResult.STOP }
  }

  fun isScopeDisposed(): Boolean {
    return module.isDisposed()
  }

  fun isInContent(fileSet: WorkspaceFileSetWithCustomData<*>): Boolean {
    return fileSet.kind.isContent
  }

  override fun getRootUrls(project: Project): Set<String> = module.rootManager.contentRootUrls.toSet()
}