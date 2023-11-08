// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.jetbrains.annotations.VisibleForTesting

interface IndexingRequestToken {
  fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp
}

@VisibleForTesting
data class IndexingRequestTokenImpl(val appIndexingRequest: AppIndexingDependenciesToken) : IndexingRequestToken {
  private val appIndexingRequestId = appIndexingRequest.toInt()
  override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
    if (file !is VirtualFileWithId) return ProjectIndexingDependenciesService.NULL_STAMP
    val fileStamp = PersistentFS.getInstance().getModificationCount(file)
    return getFileIndexingStamp(fileStamp)
  }

  @VisibleForTesting
  fun getFileIndexingStamp(fileStamp: Int): FileIndexingStamp {
    // we assume that appIndexingRequestId and file.modificationStamp never decrease => their sum only grow up
    // in the case of overflow we hope that new value does not match any previously used value
    // (which is hopefully true in most cases, because (new value)==(old value) was used veeeery long time ago)
    return WriteOnlyFileIndexingStampImpl(fileStamp + appIndexingRequestId)
  }
}