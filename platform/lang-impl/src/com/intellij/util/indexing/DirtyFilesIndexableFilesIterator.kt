// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.indexing.roots.IndexableFilesIterationMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DirtyFilesIndexableFilesIterator(private val dirtyFileIndexesCleanupFuture: Deferred<List<VirtualFile>>,
                                       private val fromOrphanQueue: Boolean) : IndexableFilesIterator {
  override fun getDebugName(): String = "dirty files iterator (from orphan queue=$fromOrphanQueue)"
  override fun getIndexingProgressText(): String = IndexingBundle.message("indexable.files.provider.indexing.files.from.previous.ide.session")
  override fun getOrigin(): IndexableSetOrigin = DirtyFilesOrigin

  override fun getRootsScanningProgressText(): String {
    return ""
  }


  override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
    val projectDirtyVirtualFiles = runBlockingCancellable { dirtyFileIndexesCleanupFuture.await() }
    return IndexableFilesIterationMethods.iterateRoots(project, projectDirtyVirtualFiles, fileIterator, fileFilter)
  }

  override fun getRootUrls(project: Project): Set<String> {
    return emptySet()
  }
}

object DirtyFilesOrigin : IndexableSetOrigin