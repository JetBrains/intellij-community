// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

// @Service(Service.Level.APP) <= TODO-ank
object FileIndexingStampService {
  private const val NULL_INDEXING_STAMP: Int = 0
  val NULL_STAMP: FileIndexingStamp = FileIndexingStampImpl(NULL_INDEXING_STAMP)

  interface FileIndexingStamp {
    /**
     * Monotonically increasing number representing IndexingStamp
     */
    fun toInt(): Int
    fun mergeWith(other: FileIndexingStamp): FileIndexingStamp
  }

  @VisibleForTesting
  data class FileIndexingStampImpl(val stamp: Int) : FileIndexingStamp {
    override fun toInt(): Int = stamp
    override fun mergeWith(other: FileIndexingStamp): FileIndexingStamp {
      return FileIndexingStampImpl(max(stamp, (other as FileIndexingStampImpl).stamp))
    }
    override fun toString(): String = stamp.toString()
  }

  private val current = AtomicReference(FileIndexingStampImpl(NULL_INDEXING_STAMP))

  @JvmStatic
  fun getCurrentStamp(): FileIndexingStamp {
    return current.get()
  }

  @JvmStatic
  fun invalidateAllStamps(): FileIndexingStamp {
    return current.updateAndGet { current ->
      val next = current.stamp + 1
      FileIndexingStampImpl(if (next == NULL_INDEXING_STAMP) NULL_INDEXING_STAMP + 1 else next)
    }
  }
}