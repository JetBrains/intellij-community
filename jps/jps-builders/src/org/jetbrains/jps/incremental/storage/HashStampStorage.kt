// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.util.ArrayUtil
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.FileHashUtil
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.FileTimestampStorage.FileTimestamp
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal class HashStampStorage(
  dataStorageRoot: Path,
  private val relativizer: PathRelativizerService,
  targetsState: BuildTargetsState,
) : AbstractStateStorage<String?, Array<HashStampPerTarget>>(
  calcStorageRoot(dataStorageRoot).resolve("data").toFile(),
  JpsCachePathStringDescriptor,
  StateExternalizer,
), StampsStorage<HashStamp?> {
  private val timestampStorage = FileTimestampStorage(dataStorageRoot, targetsState)
  private val targetState = targetsState
  private val fileStampRoot = calcStorageRoot(dataStorageRoot)

  private fun relativePath(file: File): String {
    return relativizer.toRelative(file.absolutePath)
  }

  override fun getStorageRoot(): Path = fileStampRoot

  override fun saveStamp(file: File, buildTarget: BuildTarget<*>, stamp: HashStamp) {
    timestampStorage.saveStamp(file, buildTarget, FileTimestamp.fromLong(stamp.timestamp))
    val targetId = targetState.getBuildTargetId(buildTarget)
    val path = relativePath(file)
    update(path, updateFilesStamp(oldState = getState(path), targetId = targetId, stamp = stamp))
  }

  override fun removeStamp(file: File, buildTarget: BuildTarget<*>) {
    timestampStorage.removeStamp(file, buildTarget)
    val path = relativePath(file)
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

  override fun getPreviousStamp(file: File, target: BuildTarget<*>): HashStamp? {
    val previousTimestamp = timestampStorage.getPreviousStamp(file, target) ?: return null
    val state = getState(relativePath(file)) ?: return null
    val targetId = targetState.getBuildTargetId(target)
    return state
      .firstOrNull { it.targetId == targetId }
      ?.let { HashStamp(it.hash, previousTimestamp.asLong()) }
  }

  fun getStoredFileHash(file: File, target: BuildTarget<*>): Long? {
    val state = getState(relativePath(file)) ?: return null
    val targetId = targetState.getBuildTargetId(target)
    return state.firstOrNull { it.targetId == targetId }?.hash
  }

  override fun getCurrentStamp(file: Path): HashStamp {
    val currentTimestamp = timestampStorage.getCurrentStamp(file)
    return HashStamp(FileHashUtil.getFileHash(file), currentTimestamp.asLong())
  }

  override fun isDirtyStamp(stamp: StampsStorage.Stamp, file: File): Boolean {
    if (stamp !is HashStamp) {
      return true
    }

    if (!timestampStorage.isDirtyStamp(FileTimestamp.fromLong(stamp.timestamp), file)) {
      return false
    }

    return stamp.hash != FileHashUtil.getFileHash(file.toPath())
  }

  override fun isDirtyStamp(stamp: StampsStorage.Stamp?, file: File, attrs: BasicFileAttributes): Boolean {
    if (stamp !is HashStamp) {
      return true
    }

    if (!timestampStorage.isDirtyStamp(FileTimestamp.fromLong(stamp.timestamp), file, attrs)) {
      return false
    }

    return stamp.hash != FileHashUtil.getFileHash(file.toPath())
  }

  override fun force() {
    super.force()
    timestampStorage.force()
  }

  override fun clean() {
    super.clean()
    timestampStorage.clean()
  }

  override fun wipe(): Boolean {
    return super.wipe() && timestampStorage.wipe()
  }

  override fun close() {
    super.close()
    timestampStorage.close()
  }
}

internal class HashStampPerTarget(@JvmField val targetId: Int, @JvmField val hash: Long)

internal data class HashStamp(@JvmField val hash: Long, @JvmField val  timestamp: Long) : StampsStorage.Stamp

private fun calcStorageRoot(dataStorageRoot: Path): Path = dataStorageRoot.resolve("hashes")

private fun updateFilesStamp(oldState: Array<HashStampPerTarget>?, targetId: Int, stamp: HashStamp): Array<HashStampPerTarget> {
  val newItem = HashStampPerTarget(targetId = targetId, hash = stamp.hash)
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
    }
  }

  override fun read(`in`: DataInput): Array<HashStampPerTarget> {
    val size = `in`.readInt()
    return Array(size) {
      val id = `in`.readInt()
      val hash = `in`.readLong()
      HashStampPerTarget(targetId = id, hash = hash)
    }
  }
}

private object JpsCachePathStringDescriptor : EnumeratorStringDescriptor() {
  override fun getHashCode(value: String): Int = Hashing.komihash5_0().hashCharsToInt(value)

  override fun isEqual(val1: String, val2: String) = val1 == val2
}