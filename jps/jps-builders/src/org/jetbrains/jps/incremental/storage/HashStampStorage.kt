// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.util.ArrayUtil
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentMapBuilder
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.FileHashUtil
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal class HashStampStorage(
  dataStorageRoot: Path,
  private val relativizer: PathRelativizerService,
  private val targetState: BuildTargetsState,
) : AbstractStateStorage<String, Array<HashStampPerTarget>>(
  PersistentMapBuilder.newBuilder(getStorageRoot(dataStorageRoot).resolve("data"), JpsCachePathStringDescriptor, StateExternalizer)
    .withVersion(2),
  false,
), StampsStorage<HashStamp> {
  private val fileStampRoot = getStorageRoot(dataStorageRoot)

  override fun getStorageRoot(): Path = fileStampRoot

  override fun saveStamp(file: Path, buildTarget: BuildTarget<*>, stamp: HashStamp) {
    val targetId = targetState.getBuildTargetId(buildTarget)
    val path = relativizer.toRelative(file)
    update(path, updateFilesStamp(oldState = getState(path), targetId = targetId, stamp = stamp))
  }

  override fun removeStamp(file: Path, buildTarget: BuildTarget<*>) {
    val path = relativizer.toRelative(file)
    val state = getState(path) ?: return
    val targetId = targetState.getBuildTargetId(buildTarget)
    for (i in state.indices) {
      if (state[i].targetId == targetId) {
        if (state.size == 1) {
          remove(path)
        }
        else {
          val newState = ArrayUtil.remove(state, i)
          update(path, newState)
          break
        }
      }
    }
  }

  override fun getPreviousStamp(file: Path, target: BuildTarget<*>): HashStamp? {
    val state = getState(relativizer.toRelative(file)) ?: return null
    val targetId = targetState.getBuildTargetId(target)
    return state
      .firstOrNull { it.targetId == targetId }
      ?.let { HashStamp(hash = it.hash, timestamp = it.timestamp) }
  }

  fun getStoredFileHash(file: Path, target: BuildTarget<*>): Long? {
    val state = getState(relativizer.toRelative(file)) ?: return null
    val targetId = targetState.getBuildTargetId(target)
    return state.firstOrNull { it.targetId == targetId }?.hash
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

internal class HashStampPerTarget(@JvmField val targetId: Int, @JvmField val hash: Long, @JvmField val timestamp: Long)

internal data class HashStamp(@JvmField val hash: Long, @JvmField val timestamp: Long) : StampsStorage.Stamp

private fun getStorageRoot(dataStorageRoot: Path): Path = dataStorageRoot.resolve("hashes")

private fun updateFilesStamp(oldState: Array<HashStampPerTarget>?, targetId: Int, stamp: HashStamp): Array<HashStampPerTarget> {
  val newItem = HashStampPerTarget(targetId = targetId, hash = stamp.hash, timestamp = stamp.timestamp)
  if (oldState == null) {
    return arrayOf(newItem)
  }

  var i = 0
  val length = oldState.size
  while (i < length) {
    if (oldState[i].targetId == targetId) {
      oldState[i] = newItem
      return oldState
    }
    i++
  }
  return oldState + newItem
}

private object StateExternalizer : DataExternalizer<Array<HashStampPerTarget>> {
  override fun save(out: DataOutput, value: Array<HashStampPerTarget>) {
    out.writeInt(value.size)
    for (target in value) {
      out.writeInt(target.targetId)
      out.writeLong(target.hash)
      out.writeLong(target.timestamp)
    }
  }

  override fun read(`in`: DataInput): Array<HashStampPerTarget> {
    val size = `in`.readInt()
    return Array(size) {
      val id = `in`.readInt()
      val hash = `in`.readLong()
      val timestamp = `in`.readLong()
      HashStampPerTarget(targetId = id, hash = hash, timestamp = timestamp)
    }
  }
}

private object JpsCachePathStringDescriptor : EnumeratorStringDescriptor() {
  override fun getHashCode(value: String): Int = Hashing.komihash5_0().hashCharsToInt(value)

  override fun isEqual(val1: String, val2: String) = val1 == val2
}