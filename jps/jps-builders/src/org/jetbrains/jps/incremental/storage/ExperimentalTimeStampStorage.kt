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
import org.jetbrains.jps.incremental.storage.dataTypes.LongPairKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.stringTo128BitHash
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

@ApiStatus.Internal
class ExperimentalTimeStampStorage private constructor(
  private val map: MVMap<LongArray, Long>,
  private val relativizer: PathTypeAwareRelativizer,
) : StampsStorage<Long> {
  companion object {
    @VisibleForTesting
    fun createSourceToStampMap(
      storageManager: StorageManager,
      relativizer: PathTypeAwareRelativizer,
      targetId: String,
      targetTypeId: String,
    ): ExperimentalTimeStampStorage {
      val mapName = storageManager.getMapName(targetId, targetTypeId, "file-hash-and-mtime-v1")
      return ExperimentalTimeStampStorage(
        map = storageManager.openMap(mapName, LongPairKeyDataType, LongStorageValueType),
        relativizer = relativizer,
      )
    }
  }

  override fun getStorageRoot(): Path? = null

  override fun updateStamp(file: Path, buildTarget: BuildTarget<*>?, currentFileTimestamp: Long) {
    map.put(createKey(file), currentFileTimestamp)
  }

  private fun createKey(file: Path): LongArray = stringTo128BitHash(relativizer.toRelative(file, RelativePathType.SOURCE))

  override fun removeStamp(file: Path, target: BuildTarget<*>?) {
    map.remove(createKey(file))
  }

  override fun getCurrentStampIfUpToDate(file: Path, target: BuildTarget<*>?, attrs: BasicFileAttributes?): Long? {
    return map.get(createKey(file))?.takeIf {
      it == (if (attrs == null || !attrs.isRegularFile) FSOperations.lastModified(file) else attrs.lastModifiedTime().toMillis())
    }
  }
}

private object LongStorageValueType : DataType<Long> {
  override fun isMemoryEstimationAllowed() = true

  override fun getMemory(obj: Long): Int = Long.SIZE_BYTES

  override fun createStorage(size: Int): Array<Long?> = arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    for (value in (storage as Array<Long>)) {
      buff.putVarLong(value)
    }
  }

  override fun write(buff: WriteBuffer, obj: Long) = throw IllegalStateException("Must not be called")

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<Long>
    for (i in 0 until len) {
      storage[i] = readVarLong(buff)
    }
  }

  override fun read(buff: ByteBuffer) = throw IllegalStateException("Must not be called")

  override fun compare(a: Long, b: Long) = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: Long?, storage: Any?, size: Int, initialGuess: Int) = throw IllegalStateException("Must not be called")
}