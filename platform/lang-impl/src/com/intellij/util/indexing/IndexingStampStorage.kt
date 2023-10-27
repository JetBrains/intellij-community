// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.util.indexing.TimestampsImmutable.Companion.readTimestamps
import com.intellij.util.indexing.impl.perFileVersion.IntFileAttribute
import com.intellij.util.io.CachingEnumerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.io.IOException


sealed interface IndexingStampStorage : Closeable {

  @Throws(IOException::class)
  fun writeTimestamps(fileId: Int, timestamps: TimestampsImmutable)

  fun readTimestamps(fileId: Int): TimestampsImmutable?
}

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
    private const val MARKER_FILE_ID = 1
  }

  private val enumerator: PersistentTimestampsEnumerator
  private val enumeratorCache: CachingEnumerator<TimestampsImmutable>
  private val persistence = IntFileAttribute.overFastAttribute(PERSISTENCE)

  init {
    // Don't put this to getIndexRoot()/fast_index_stamps. Shared indexes don't like it. Check SharedIndexesTest
    val dir = PathManager.getSystemDir().resolve("caches/fast_index_stamps")

    val recreated = persistence.readInt(MARKER_FILE_ID)
    if (recreated == 0) { // Mark absent. Probably, VFS was recreated
      thisLogger().info("Recreating IndexingStampStorageOverFastAttributes because VFS was recreated")
      FileUtil.deleteWithRenamingIfExists(dir)
      persistence.writeInt(MARKER_FILE_ID, 1)
    }

    enumerator = PersistentTimestampsEnumerator(dir.resolve("fast_index_stamps_keys"))
    enumeratorCache = CachingEnumerator(enumerator, TimestampsKeyDescriptor())

    // this is exactly the same way as persistence:IntFileAttribute is closed
    FSRecords.getInstance().addCloseable(enumerator)
  }

  @Throws(IOException::class)
  override fun writeTimestamps(fileId: Int, timestamps: TimestampsImmutable) {
    val value = enumeratorCache.enumerate(timestamps)
    // use force to stay on safe side (especially noticeable in tests, where runnables submitted via FSRecords.addCloseable
    // are not invoked, and enumerator is not flushed)
    enumerator.force()
    persistence.writeInt(fileId, value)
  }

  override fun readTimestamps(fileId: Int): TimestampsImmutable? {
    try {
      val value = persistence.readInt(fileId)
      if (value == 0) return null
      return enumeratorCache.valueOf(value)
    }
    catch (e: IOException) {
      throw FSRecords.handleError(e)
    }
  }

  override fun close() {
    persistence.close()
    enumerator.close()
  }
}