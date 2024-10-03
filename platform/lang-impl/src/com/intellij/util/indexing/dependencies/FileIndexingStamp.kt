// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.LongConsumer

private const val NULL_INDEXING_STAMP: Long = 0
typealias IndexingRequestIdAndFileModCount = Long
typealias FileModCount = Int

@ApiStatus.Internal
fun IndexingRequestIdAndFileModCount.toFileModCount(): FileModCount = this.toInt()
@ApiStatus.Internal
fun FileModCount.withIndexingRequestId(indexingRequestId: Int): IndexingRequestIdAndFileModCount {
  // https://stackoverflow.com/a/12772968 FileModCount shouldn't be negative but let's combine numbers properly
  return (indexingRequestId.toLong() shl 32) or (0xffffffffL and this.toLong())
}

@ApiStatus.Internal
enum class IsFileChangedResult {
  YES,
  NO,
  UNKNOWN
}

@ApiStatus.Internal
interface FileIndexingStamp {
  /**
   * Number representing IndexingStamp. Use [isFileChanged] to compare this number to any other stamps.
   * Signature made complicated intentionally to make it harder to obtain int and compare it with some other int
   * obtained from another FileIndexingStamp. Comparison should onlu be made via [isFileChanged]
   */
  fun store(storage: LongConsumer)

  /**
   * Compares this stamp to Long value (request id, file mod count) obtained via [store]
   */
  fun isSame(i: IndexingRequestIdAndFileModCount): Boolean

  /**
   * Compares this stamp to Long value obtained via [store]
   */
  fun isFileChanged(i: FileModCount): IsFileChangedResult
}

internal object NullIndexingStamp : FileIndexingStamp {
  override fun store(storage: LongConsumer) {
    storage.accept(NULL_INDEXING_STAMP)
  }

  override fun isSame(i: IndexingRequestIdAndFileModCount): Boolean = false

  override fun isFileChanged(i: FileModCount) = IsFileChangedResult.UNKNOWN
}

private fun isFileChanged(i: FileModCount, stamp: Long): IsFileChangedResult {
  return if (i == stamp.toFileModCount()) IsFileChangedResult.NO
  else IsFileChangedResult.YES
}

@ApiStatus.Internal
@VisibleForTesting
data class ReadWriteFileIndexingStampImpl(val stamp: Long, private val allowCheckingForOutdatedIndexesUsingFileModCount: Boolean = false) : FileIndexingStamp {
  override fun store(storage: LongConsumer) {
    storage.accept(stamp)
  }

  override fun isSame(i: IndexingRequestIdAndFileModCount): Boolean {
    return i != NULL_INDEXING_STAMP && i == stamp
  }

  override fun isFileChanged(i: FileModCount): IsFileChangedResult {
    return if (allowCheckingForOutdatedIndexesUsingFileModCount) isFileChanged(i, stamp)
    else IsFileChangedResult.UNKNOWN
  }

  companion object {
    fun create(requestId: Int, fileStamp: Int, expectIndexesWereNotRemoved: Boolean): FileIndexingStamp {
      return ReadWriteFileIndexingStampImpl(fileStamp.withIndexingRequestId(requestId), expectIndexesWereNotRemoved)
    }
  }
}

@ApiStatus.Internal
@VisibleForTesting
data class WriteOnlyFileIndexingStampImpl(val stamp: Long, private val allowCheckingForOutdatedIndexesUsingFileModCount: Boolean = false) : FileIndexingStamp {
  override fun store(storage: LongConsumer) {
    storage.accept(stamp)
  }

  override fun isSame(i: IndexingRequestIdAndFileModCount): Boolean = false

  override fun isFileChanged(i: FileModCount): IsFileChangedResult {
    return if (allowCheckingForOutdatedIndexesUsingFileModCount) isFileChanged(i, stamp)
    else IsFileChangedResult.UNKNOWN
  }

  companion object {
    fun create(requestId: Int, fileStamp: Int, allowCheckingForOutdatedIndexesUsingFileModCount: Boolean): FileIndexingStamp {
      return WriteOnlyFileIndexingStampImpl(requestId.toLong().shl(32) + fileStamp, allowCheckingForOutdatedIndexesUsingFileModCount)
    }
  }
}
