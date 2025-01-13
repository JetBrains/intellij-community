// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.incremental.storage

import org.h2.mvstore.DataUtils.readVarLong
import org.h2.mvstore.MVMap
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.FileHashUtil
import org.jetbrains.jps.incremental.storage.dataTypes.LongPairKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.stringTo128BitHash
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

@ApiStatus.Internal
class HashStampStorage private constructor(
  private val map: MVMap<LongArray, HashStamp>,
  private val relativizer: PathTypeAwareRelativizer,
) : StampsStorage<HashStamp> {
  companion object {
    @VisibleForTesting
    fun createSourceToStampMap(
      storageManager: StorageManager,
      relativizer: PathTypeAwareRelativizer,
      targetId: String,
      targetTypeId: String,
    ): HashStampStorage {
      val mapName = storageManager.getMapName(targetId = targetId, targetTypeId = targetTypeId, suffix = "file-hash-and-mtime-v1")
      return HashStampStorage(
        map = storageManager.openMap(mapName, LongPairKeyDataType, HashStampStorageValueType),
        relativizer = relativizer,
      )
    }
  }

  override fun getStorageRoot(): Path? = null

  override fun updateStamp(file: Path, buildTarget: BuildTarget<*>?, currentFileTimestamp: Long) {
    map.put(createKey(file), HashStamp(hash = FileHashUtil.getFileHash(file), timestamp = currentFileTimestamp))
  }

  private fun createKey(file: Path): LongArray = stringTo128BitHash(relativizer.toRelative(file, RelativePathType.SOURCE))

  override fun removeStamp(file: Path, target: BuildTarget<*>?) {
    map.remove(createKey(file))
  }

  fun getStoredFileStamp(file: Path): HashStamp? {
    return map.get(createKey(file))
  }

  override fun getCurrentStampIfUpToDate(file: Path, target: BuildTarget<*>?, attrs: BasicFileAttributes?): HashStamp? {
    return map.get(createKey(file))?.takeIf {
      val timestamp = if (attrs == null || !attrs.isRegularFile) FSOperations.lastModified(file) else attrs.lastModifiedTime().toMillis()
      timestamp == it.timestamp || it.hash == FileHashUtil.getFileHash(file)
    }
  }
}

@ApiStatus.Internal
@VisibleForTesting
class HashStamp(@JvmField val hash: Long, @JvmField val timestamp: Long)

private object HashStampStorageValueType : DataType<HashStamp> {
  override fun isMemoryEstimationAllowed() = true

  override fun getMemory(obj: HashStamp): Int = Long.SIZE_BYTES + Long.SIZE_BYTES

  override fun createStorage(size: Int): Array<HashStamp?> = arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    for (value in (storage as Array<HashStamp>)) {
      buff.putLong(value.hash)
      buff.putVarLong(value.timestamp)
    }
  }

  override fun write(buff: WriteBuffer, obj: HashStamp) = throw IllegalStateException("Must not be called")

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<HashStamp>
    for (i in 0 until len) {
      storage[i] = HashStamp(hash = buff.getLong(), timestamp = readVarLong(buff))
    }
  }

  override fun read(buff: ByteBuffer) = throw IllegalStateException("Must not be called")

  override fun compare(a: HashStamp, b: HashStamp) = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: HashStamp?, storage: Any?, size: Int, initialGuess: Int) = throw IllegalStateException("Must not be called")
}