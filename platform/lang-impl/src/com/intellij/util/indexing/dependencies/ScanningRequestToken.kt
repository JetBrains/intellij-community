// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
abstract class ScanningRequestToken {
  @Volatile
  private var successful = true
  abstract val appIndexingRequestId: AppIndexingDependenciesToken
  abstract fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp
  fun markUnsuccessful() {
    successful = false
  }

  fun isSuccessful() = successful
}

internal object RequestFullHeavyScanningToken : ScanningRequestToken() {
  override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
    throw IllegalStateException("This token is a marker. It should not be used.")
  }

  override val appIndexingRequestId: AppIndexingDependenciesToken
    get() = throw IllegalStateException("This token is a marker. It should not be used.")
}


@ApiStatus.Internal
@VisibleForTesting
class WriteOnlyScanningRequestTokenImpl(appIndexingRequest: AppIndexingDependenciesToken, private val allowCheckingForOutdatedIndexesUsingFileModCount: Boolean) : ScanningRequestToken() {
  override val appIndexingRequestId: AppIndexingDependenciesToken = appIndexingRequest
  override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
    if (file !is VirtualFileWithId) return ProjectIndexingDependenciesService.NULL_STAMP
    val fileStamp = PersistentFS.getInstance().getModificationCount(file)
    return getFileIndexingStamp(fileStamp)
  }

  @VisibleForTesting
  fun getFileIndexingStamp(fileStamp: Int): FileIndexingStamp {
    return WriteOnlyFileIndexingStampImpl.create(appIndexingRequestId.toInt(), fileStamp, allowCheckingForOutdatedIndexesUsingFileModCount)
  }
}


@ApiStatus.Internal
@VisibleForTesting
class ReadWriteScanningRequestTokenImpl(appIndexingRequest: AppIndexingDependenciesToken, private val allowCheckingForOutdatedIndexesUsingFileModCount: Boolean) : ScanningRequestToken() {
  override val appIndexingRequestId: AppIndexingDependenciesToken = appIndexingRequest
  override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
    if (file !is VirtualFileWithId) return ProjectIndexingDependenciesService.NULL_STAMP
    val fileStamp = PersistentFS.getInstance().getModificationCount(file)
    return getFileIndexingStamp(fileStamp)
  }

  @VisibleForTesting
  fun getFileIndexingStamp(fileStamp: Int): FileIndexingStamp {
    return ReadWriteFileIndexingStampImpl.create(appIndexingRequestId.toInt(), fileStamp, allowCheckingForOutdatedIndexesUsingFileModCount)
  }
}