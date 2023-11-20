// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.google.common.io.Closer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.util.io.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

/**
 * A class that allows associating enumerable objects and virtual files.
 * Physical storage contains two pieces: [DurableDataEnumerator] and [IntFileAttribute].
 * Physical storage ([exclusiveDir] directory with all its content) will be deleted on VFS rebuild.
 *
 * The main problems that this class is intended to solve:
 * 1. store files used for enumerated data ([DurableDataEnumerator]) and [IntFileAttribute] in the same folder
 * 2. delete all the data on VFS rebuild
 *
 * @param exclusiveDir a directory to use for storage. [EnumeratedFastFileAttribute] assumes that it
 * uses this directory exclusively, i.e. no other classes keep their files there. [EnumeratedFastFileAttribute] will
 * delete the whole directory on VFS rebuild. All the data is kept in this folder only, i.e. it is enough to
 * delete it (while [EnumeratedFastFileAttribute] is closed) to drop all the existing data.
 * @param fileAttribute
 * @param descriptorForCache enable caching via [CachingEnumerator] (or use no-cache if `null`)
 * @param createEnumerator lambda that creates [DurableDataEnumerator] in specified `enumeratorPath`. Invoked at most once (exactly once if
 * constructor completes normally).
 */
@Internal
class EnumeratedFastFileAttribute<T> @VisibleForTesting constructor(private val exclusiveDir: Path,
                                                                    fileAttribute: FileAttribute,
                                                                    descriptorForCache: KeyDescriptor<T>?,
                                                                    expectedVfsCreationTimestamp: Long,
                                                                    createEnumerator: (enumeratorPath: Path) -> DurableDataEnumerator<T>) : Closeable {

  private val baseEnumerator: DataEnumerator<T>
  private val baseAttribute: IntFileAttribute
  private val closer: Closer = Closer.create()

  constructor(exclusiveDir: Path,
              fileAttribute: FileAttribute,
              descriptorForCache: KeyDescriptor<T>?,
              createEnumerator: (enumeratorPath: Path) -> DurableDataEnumerator<T>) :
    this(exclusiveDir, fileAttribute, descriptorForCache, FSRecords.getCreationTimestamp(), createEnumerator)

  init {
    val vfsChecker = VfsCreationStampChecker(getVfsCreationTimestampFile())
    assert(!exclusiveDir.isRegularFile()) { "$exclusiveDir should be a directory, or non-existing path" }

    vfsChecker.runIfVfsCreationStampMismatch(expectedVfsCreationTimestamp) { cleanupReason ->
      deleteStorageDir(cleanupReason)
    }

    val (enumerator, attribute) = try {
      tryOpenStorages(fileAttribute, createEnumerator)
    }
    catch (ioe: IOException) {
      deleteStorageDir(ioe.toString())
      tryOpenStorages(fileAttribute, createEnumerator)
    }

    closer.register(enumerator)
    closer.register(attribute)

    baseEnumerator = if (descriptorForCache == null) enumerator else CachingEnumerator(enumerator, descriptorForCache)
    baseAttribute = attribute
    vfsChecker.createVfsTimestampMarkerFileIfAbsent(expectedVfsCreationTimestamp)
  }

  private fun tryOpenStorages(fileAttribute: FileAttribute, createEnumerator: (enumeratorPath: Path) -> DurableDataEnumerator<T>)
    : Pair<DurableDataEnumerator<T>, IntFileAttribute> {
    val localCloser = Closer.create()
    try {
      val enumerator = createEnumerator(getEnumeratorFile())
      localCloser.register(enumerator)

      val attribute = IntFileAttribute.overFastAttribute(fileAttribute, getAttributesFile())
      localCloser.register(attribute)

      return Pair(enumerator, attribute)
    }
    catch (e: Throwable) {
      localCloser.close()
      throw e
    }
  }

  private fun getAttributesFile(): Path = exclusiveDir.resolve("attributes")

  private fun getEnumeratorFile(): Path = exclusiveDir.resolve("enumerator")

  private fun getVfsCreationTimestampFile(): Path = exclusiveDir.resolve("vfs.stamp")

  private fun deleteStorageDir(cleanupReason: String) {
    thisLogger().info("Clear $exclusiveDir. Reason: $cleanupReason")
    FileUtil.deleteWithRenamingIfExists(exclusiveDir)
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