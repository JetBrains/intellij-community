// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.google.common.io.Closer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.util.io.*
import com.intellij.util.io.outputStream
import com.jetbrains.rd.util.parseLong
import com.jetbrains.rd.util.putLong
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/**
 * A class that allows associating enumerable objects and virtual files.
 * Physical storage contains two pieces: [PersistentEnumerator] and [IntFileAttribute].
 * Physical storage ([exclusiveDir] directory with all its content) will be deleted on VFS rebuild.
 *
 * The main problems that this class is intended to solve:
 * 1. store files used for enumerated data ([PersistentEnumerator]) and [IntFileAttribute] in the same folder
 * 2. delete all the data on VFS rebuild
 *
 * @param exclusiveDir a directory to use for storage. [EnumeratedFastFileAttribute] assumes that it
 * uses this directory exclusively, i.e. no other classes keep their files there. [EnumeratedFastFileAttribute] will
 * delete the whole directory on VFS rebuild. All the data is kept in this folder only, i.e. it is enough to
 * delete it (while [EnumeratedFastFileAttribute] is closed) to drop all the existing data.
 * @param attribute
 * @param descriptorForCache enable caching via [CachingEnumerator] (or use no-cache if `null`)
 * @param createEnumerator lambda that creates [PersistentEnumerator] in specified `enumeratorPath`. Invoked at most once (exactly once if
 * constructor completes normally).
 */
@Internal
class EnumeratedFastFileAttribute<T> @VisibleForTesting constructor(private val exclusiveDir: Path,
                                                                    attribute: FileAttribute,
                                                                    descriptorForCache: KeyDescriptor<T>?,
                                                                    expectedVfsCreationTimestamp: Long,
                                                                    createEnumerator: (enumeratorPath: Path) -> PersistentEnumerator<T>) : Closeable {

  private val baseEnumerator: DataEnumerator<T>
  private val baseAttribute: IntFileAttribute
  private val closer: Closer = Closer.create()

  constructor(exclusiveDir: Path,
              attribute: FileAttribute,
              descriptorForCache: KeyDescriptor<T>?,
              createEnumerator: (enumeratorPath: Path) -> PersistentEnumerator<T>) :
    this(exclusiveDir, attribute, descriptorForCache, FSRecords.getCreationTimestamp(), createEnumerator)

  init {
    assert(!exclusiveDir.isRegularFile()) { "$exclusiveDir should be a directory, or non-existing path" }

    cleanupIfVfsCreationStampMismatch(expectedVfsCreationTimestamp)

    val persistentEnumerator = createEnumerator(getEnumeratorFile())
    closer.register(persistentEnumerator)
    baseEnumerator = if (descriptorForCache == null) persistentEnumerator else CachingEnumerator(persistentEnumerator, descriptorForCache)

    baseAttribute = IntFileAttribute.overFastAttribute(attribute, getAttributesFile())
    closer.register(baseAttribute)

    createVfsTimestampMarkerFileIfAbsent(expectedVfsCreationTimestamp)
  }

  private fun createVfsTimestampMarkerFileIfAbsent(expectedVfsCreationTimestamp: Long) {
    val vfsCreationTimestampFile = getVfsCreationTimestampFile()
    if (!vfsCreationTimestampFile.isRegularFile()) {
      val expectedVfsCreationTimestampBytes = ByteArray(Long.SIZE_BYTES)
      expectedVfsCreationTimestampBytes.putLong(expectedVfsCreationTimestamp, 0)
      vfsCreationTimestampFile.outputStream().use { out -> out.write(expectedVfsCreationTimestampBytes) }
    }
  }

  private fun getAttributesFile(): Path = exclusiveDir.resolve("attributes")

  private fun getEnumeratorFile(): Path = exclusiveDir.resolve("enumerator")

  private fun getVfsCreationTimestampFile(): Path = exclusiveDir.resolve("vfs.stamp")

  private fun cleanupIfVfsCreationStampMismatch(expectedVfsCreationTimestamp: Long) {
    if (exclusiveDir.exists()) {
      // directory exists. Check VFS creation timestamp and drop the file if it is outdated
      val vfsCreationTimestampPath = getVfsCreationTimestampFile()
      var cleanupReason: String? = null
      if (vfsCreationTimestampPath.isRegularFile()) {
        val read = vfsCreationTimestampPath.inputStream().use { it.readNBytes(Long.SIZE_BYTES) }
        if (read.size != Long.SIZE_BYTES) {
          cleanupReason = "$vfsCreationTimestampPath has only ${read.size} bytes (${read.toList()})"
        }
        else {
          val storedTimestamp = read.parseLong(0)
          if (expectedVfsCreationTimestamp != storedTimestamp) {
            cleanupReason = "expected VFS creation timestamp = $expectedVfsCreationTimestamp, stored VFS creation timestamp = $storedTimestamp"
          }
        }
      }
      else {
        cleanupReason = "$vfsCreationTimestampPath is not a file"
      }

      if (cleanupReason != null) {
        thisLogger().info("Clear $exclusiveDir. Reason: $cleanupReason")
        FileUtil.deleteWithRenamingIfExists(exclusiveDir)
      }
    }
  }

  @Throws(IOException::class)
  fun readEnumerated(fileId: Int): T? {
    val enumValue = baseAttribute.readInt(fileId)
    return if (enumValue > 0) baseEnumerator.valueOf(enumValue) else null
  }

  @Throws(IOException::class)
  fun writeEnumerated(fileId: Int, value: T) {
    val enumValue = baseEnumerator.enumerate(value)
    baseAttribute.writeInt(fileId, enumValue)
  }

  @Throws(IOException::class)
  fun clearValue(fileId: Int) {
    baseAttribute.writeInt(fileId, 0)
  }

  @Throws(IOException::class)
  override fun close() {
    closer.close()
  }
}