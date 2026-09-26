// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.util.indexing.TimestampsImmutable.Companion.readTimestamps
import com.intellij.util.indexing.impl.perFileVersion.EnumeratedFastFileAttribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.io.IOException


@ApiStatus.Internal
sealed interface IndexingStampStorage : Closeable {

  @Throws(IOException::class)
  fun writeTimestamps(fileId: Int, timestamps: TimestampsImmutable)

  fun readTimestamps(fileId: Int): TimestampsImmutable?
}

@ApiStatus.Internal
@VisibleForTesting
class IndexingStampStorageOverRegularAttributes : IndexingStampStorage {
  companion object {
    @VisibleForTesting
    @ApiStatus.Internal
    @JvmField
    val PERSISTENCE: FileAttribute = FileAttribute("__index_stamps__", 2, false)
  }

  @Throws(IOException::class)
  override fun writeTimestamps(fileId: Int, timestamps: TimestampsImmutable) {
    FSRecords.writeAttribute(fileId, PERSISTENCE).use { out ->
      timestamps.writeToStream(out)
    }
  }

  override fun readTimestamps(fileId: Int): TimestampsImmutable? {
    try {
      return if (FSRecords.supportsRawAttributesAccess()) {
        FSRecords.readAttributeRawWithLock(fileId, PERSISTENCE, TimestampsImmutable::readTimestamps)
      }
      else {
        FSRecords.readAttributeWithLock(fileId, PERSISTENCE)?.use { stream ->
          readTimestamps(stream)
        }
      }
    }
    catch (e: IOException) {
      throw FSRecords.handleError(e)
    }
  }

  override fun close() {
    // noop
  }
}


internal class IndexingStampStorageOverFastAttributes : IndexingStampStorage {
  companion object {
    val PERSISTENCE: FileAttribute = FileAttribute("__fast_index_stamps__", 0, true)
  }

  private val persistence: EnumeratedFastFileAttribute<TimestampsImmutable>

  init {
    val dir = PathManager.getIndexRoot().resolve("fast_index_stamps")

    persistence = EnumeratedFastFileAttribute(dir, PERSISTENCE, TimestampsKeyDescriptor()) { enumeratorPath ->
      createTimestampsEnumerator(enumeratorPath)
    }
  }

  @Throws(IOException::class)
  override fun writeTimestamps(fileId: Int, timestamps: TimestampsImmutable) {
    persistence.writeEnumerated(fileId, timestamps)
  }

  override fun readTimestamps(fileId: Int): TimestampsImmutable? {
    try {
      return persistence.readEnumerated(fileId)
    }
    catch (e: IOException) {
      //TODO RC: why we blame VFS if there is something wrong with storages unrelated to VFS?
      //         It may make sense for IndexingStampStorageOverRegularAttributes there VFS file attributes
      //         are used to store indexing stamps -- but fast attributes storage are unrelated to VFS
      throw FSRecords.handleError(e)
    }
  }

  override fun close() {
    persistence.close()
  }
}