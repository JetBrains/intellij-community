// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.incremental.storage

import com.dynatrace.hash4j.hashing.Hashing
import org.h2.mvstore.DataUtils.readVarInt
import org.h2.mvstore.DataUtils.readVarLong
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.DataType
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.FileHashUtil
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal class HashStampStorage(
  private val storageManager: StorageManager,
  private val relativizer: PathRelativizerService,
  private val targetState: BuildTargetsState,
) : StampsStorage<HashStamp> {
  private val mapHandle = storageManager.openMap("file-hash-and-mtime-v1", HashStampStorageKeyType, HashStampStorageValueType)

  override fun getStorageRoot(): Path = storageManager.file

  override fun saveStamp(file: Path, buildTarget: BuildTarget<*>, stamp: HashStamp) {
    mapHandle.map.put(createKey(buildTarget, file), stamp)
  }

  private fun createKey(target: BuildTarget<*>, file: Path): HashStampStorageKey {
    return HashStampStorageKey(
      targetId = targetState.getBuildTargetId(target),
      // getBytes is faster (70k op/s vs. 50 op/s)
      // use xxh3_64 as it is more proven hash algo than komihash
      pathHash = Hashing.xxh3_64().hashBytesToLong(relativizer.toRelative(file).toByteArray()),
    )
  }

  override fun removeStamp(file: Path, target: BuildTarget<*>) {
    mapHandle.map.remove(createKey(target, file))
  }

  override fun getPreviousStamp(file: Path, target: BuildTarget<*>): HashStamp? {
    return mapHandle.map.get(createKey(target, file))
  }

  fun getStoredFileHash(file: Path, target: BuildTarget<*>): Long? {
    return mapHandle.map.get(createKey(target, file))?.hash
  }

  override fun getCurrentStamp(file: Path): HashStamp {
    return HashStamp(hash = FileHashUtil.getFileHash(file), timestamp = FSOperations.lastModified(file))
  }

  override fun isDirtyStamp(stamp: StampsStorage.Stamp, file: Path): Boolean {
    if (stamp !is HashStamp) {
      return true
    }
    if (stamp.timestamp == FSOperations.lastModified(file)) {
      return false
    }
    return stamp.hash != FileHashUtil.getFileHash(file)
  }

  override fun isDirtyStamp(stamp: StampsStorage.Stamp?, file: Path, attrs: BasicFileAttributes): Boolean {
    if (stamp !is HashStamp) {
      return true
    }

    // If equal, then non-dirty.
    // If not equal, then we check the hash to avoid marking the file as `dirty` only because of a different timestamp.
    // We cannot rely solely on the hash, as getting the last-modified timestamp is much cheaper than computing the file hash.
    if ((if (attrs.isRegularFile) attrs.lastModifiedTime().toMillis() else FSOperations.lastModified(file)) == stamp.timestamp) {
      return false
    }

    return stamp.hash != FileHashUtil.getFileHash(file)
  }
}

internal class HashStamp(@JvmField val hash: Long, @JvmField val timestamp: Long) : StampsStorage.Stamp

private class HashStampStorageKey(@JvmField val targetId: Int, @JvmField val pathHash: Long)

private object HashStampStorageKeyType : DataType<HashStampStorageKey> {
  override fun isMemoryEstimationAllowed() = true

  override fun getMemory(obj: HashStampStorageKey): Int = Int.SIZE_BYTES + Long.SIZE_BYTES

  override fun createStorage(size: Int): Array<HashStampStorageKey?> = arrayOfNulls(size)

  override fun write(buff: WriteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    for (key in (storage as Array<HashStampStorageKey>)) {
      buff.putVarInt(key.targetId)
      // not var long - maybe negative number
      buff.putLong(key.pathHash)
    }
  }

  override fun write(buff: WriteBuffer, obj: HashStampStorageKey) = throw IllegalStateException("Must not be called")

  override fun read(buff: ByteBuffer, storage: Any, len: Int) {
    @Suppress("UNCHECKED_CAST")
    storage as Array<HashStampStorageKey>
    for (i in 0 until len) {
      storage[i] = HashStampStorageKey(targetId = readVarInt(buff), pathHash = buff.getLong())
    }
  }

  override fun read(buff: ByteBuffer) = throw IllegalStateException("Must not be called")

  override fun binarySearch(key: HashStampStorageKey, storage: Any, size: Int, initialGuess: Int): Int {
    @Suppress("UNCHECKED_CAST")
    storage as Array<HashStampStorageKey>

    var low = 0
    var high = size - 1
    // the cached index minus one, so that for the first time (when cachedCompare is 0), the default value is used
    var x = initialGuess - 1
    if (x < 0 || x > high) {
      x = high ushr 1
    }
    while (low <= high) {
      val b = storage[x]
      val compare = when {
        key.targetId > b.targetId -> 1
        key.targetId < b.targetId -> -1
        key.pathHash > b.pathHash -> 1
        key.pathHash < b.pathHash -> -1
        else -> 0
      }

      when {
        compare > 0 -> low = x + 1
        compare < 0 -> high = x - 1
        else -> return x
      }
      x = (low + high) ushr 1
    }
    return low.inv()
  }

  @Suppress("DuplicatedCode")
  override fun compare(a: HashStampStorageKey, b: HashStampStorageKey): Int {
    return when {
      a.targetId > b.targetId -> 1
      a.targetId < b.targetId -> -1
      a.pathHash > b.pathHash -> 1
      a.pathHash < b.pathHash -> -1
      else -> 0
    }
  }
}

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