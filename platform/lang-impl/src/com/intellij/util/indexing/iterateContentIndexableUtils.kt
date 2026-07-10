// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex

internal fun ContentIterator.unwrapCacheAvoiding(): ContentIterator = ContentIterator { file ->
  when {
    file !is CacheAvoidingVirtualFile -> this.processFile(file)

    else -> when (val cacheableFile = file.asCacheable()) {
      null -> true
      else -> this.processFile(cacheableFile)
    }
  }
}

internal fun ContentIteratorEx.unwrapCacheAvoiding(): ContentIteratorEx = ContentIteratorEx { file ->
  when {
    file !is CacheAvoidingVirtualFile -> this.processFileEx(file)

    else -> when (val cacheableFile = file.asCacheable()) {
      null -> TreeNodeProcessingResult.CONTINUE
      else -> this.processFileEx(cacheableFile)
    }
  }
}

internal fun VirtualFileFilter.andIndexable(workspaceFileIndex: WorkspaceFileIndex): VirtualFileFilter =
  this.and { file -> workspaceFileIndex.isIndexable(file) }


