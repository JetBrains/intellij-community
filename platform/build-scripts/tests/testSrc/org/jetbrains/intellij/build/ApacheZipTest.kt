// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.intellij.build.impl.dir
import org.jetbrains.intellij.build.io.writeNewFile
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ApacheZipTest {
  @Test
  fun symlink(@TempDir tempDir: Path) {
    Assumptions.assumeTrue(SystemInfoRt.isUnix)

    val dir = tempDir.resolve("dir")
    Files.createDirectories(dir)

    val targetFile = dir.resolve("target")
    Files.writeString(targetFile, "target")
    Files.createSymbolicLink(dir.resolve("link"), targetFile)

    val zipFile = tempDir.resolve("file.zip")
    writeNewFile(zipFile) { outFileChannel ->
      ZipArchiveOutputStream(outFileChannel).use { out -> out.dir(dir, "") }
    }
  }
}