// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.util.ThreeState
import com.intellij.util.io.zip.JBZipFile
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Path

@Suppress("SuspiciousPackagePrivateAccess")
internal class JBZipFileWrapperTest {
  @Test
  fun `reads a ZIP64 archive`(@TempDir tempDir: Path) {
    val archive = tempDir.resolve("archive.zip")
    createZip64Archive(archive)

    val zipFile = JBZipFileWrapper(archive)
    try {
      assertEquals(1, zipFile.entries.size, "The ZIP64 central directory must expose all entries")

      val marker = zipFile.getEntry(MARKER_ENTRY)
      assertNotNull(marker, "The marker entry must be available in the ZIP64 archive")
      val markerStream = marker!!.inputStream
      assertNotNull(markerStream, "The marker entry must provide its content")
      assertArrayEquals(MARKER_CONTENT, markerStream!!.use { it.readAllBytes() }, "The marker entry content must stay intact")
    }
    finally {
      zipFile.close()
    }
  }

  private fun createZip64Archive(archive: Path) {
    JBZipFile(archive, StandardCharsets.UTF_8, /* readonly = */ false, /* isZip64 = */ ThreeState.YES).use { zipFile ->
      zipFile.getOrCreateEntry(MARKER_ENTRY).data = MARKER_CONTENT
    }
  }

  private companion object {
    const val MARKER_ENTRY = "marker.txt"
    val MARKER_CONTENT = "marker content".toByteArray()
  }
}
