// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.outputStream
import com.jetbrains.rd.util.parseLong
import com.jetbrains.rd.util.putLong
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

@ApiStatus.Internal
class VfsCreationStampChecker(private val vfsCreationTimestampPath: Path) {
  fun runIfVfsCreationStampMismatch(expectedVfsCreationTimestamp: Long, cleanup: (reason: String) -> Unit) {
    if (vfsCreationTimestampPath.parent.exists()) {
      // directory exists. Check VFS creation timestamp and drop the file if it is outdated
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
        cleanup(cleanupReason)
      }
    }
  }

  fun createVfsTimestampMarkerFileIfAbsent(expectedVfsCreationTimestamp: Long) {
    if (!vfsCreationTimestampPath.isRegularFile()) {
      val expectedVfsCreationTimestampBytes = ByteArray(Long.SIZE_BYTES)
      expectedVfsCreationTimestampBytes.putLong(expectedVfsCreationTimestamp, 0)
      vfsCreationTimestampPath.outputStream().use { out -> out.write(expectedVfsCreationTimestampBytes) }
    }
  }

  fun deleteCreationStamp() {
    FileUtil.deleteWithRenamingIfExists(vfsCreationTimestampPath)
  }
}