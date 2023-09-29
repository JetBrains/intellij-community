// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.jetbrains.annotations.VisibleForTesting

interface ScanningRequestToken {
  /**
   * Monotonically increasing number representing IndexingStamp
   */
  fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp
}

@VisibleForTesting
class WriteOnlyScanningRequestTokenImpl(val requestId: Int,
                                        appIndexingRequest: AppIndexingDependenciesToken) : ScanningRequestToken {
  private val appIndexingRequestId = appIndexingRequest.toInt()
  override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
    if (file !is VirtualFileWithId) return ProjectIndexingDependenciesService.NULL_STAMP
    val fileStamp = PersistentFS.getInstance().getModificationCount(file)
    return getFileIndexingStamp(fileStamp)
  }

  @VisibleForTesting
  fun getFileIndexingStamp(fileStamp: Int): FileIndexingStamp {
    return WriteOnlyFileIndexingStampImpl(fileStamp + requestId + appIndexingRequestId)
  }
}


@VisibleForTesting
class ReadWriteScanningRequestTokenImpl(val requestId: Int,
                                        appIndexingRequest: AppIndexingDependenciesToken) : ScanningRequestToken {
  private val appIndexingRequestId = appIndexingRequest.toInt()
  override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
    if (file !is VirtualFileWithId) return ProjectIndexingDependenciesService.NULL_STAMP
    val fileStamp = PersistentFS.getInstance().getModificationCount(file)
    return getFileIndexingStamp(fileStamp)
  }

  @VisibleForTesting
  fun getFileIndexingStamp(fileStamp: Int): FileIndexingStamp {
    return ReadWriteFileIndexingStampImpl(fileStamp + requestId + appIndexingRequestId)
  }
}