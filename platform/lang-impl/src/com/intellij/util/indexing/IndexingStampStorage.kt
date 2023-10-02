// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.util.indexing.TimestampsImmutable.Companion.readTimestamps
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException


@VisibleForTesting
class IndexingStampStorage {
  companion object {
    @VisibleForTesting
    @ApiStatus.Internal
    @JvmField
    val PERSISTENCE: FileAttribute = FileAttribute("__index_stamps__", 2, false)
  }

  @Throws(IOException::class)
  fun writeTimestamps(fileId: Int, timestamps: TimestampsImmutable) {
    FSRecords.writeAttribute(fileId, PERSISTENCE).use { out ->
      timestamps.writeToStream(out)
    }
  }

  fun readTimestamps(fileId: Int): TimestampsImmutable? {
    return if (FSRecords.supportsRawAttributesAccess()) {
      FSRecords.readAttributeRawWithLock(fileId, PERSISTENCE, TimestampsImmutable::readTimestamps)
    }
    else {
      try {
        FSRecords.readAttributeWithLock(fileId, PERSISTENCE)?.use { stream ->
          readTimestamps(stream)
        }
      }
      catch (e: IOException) {
        throw FSRecords.handleError(e)
      }
    }
  }
}