// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.org.jetbrains.intellij.build.impl.sbom

import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.impl.Checksums
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class ChecksumsTest {
  @Test
  fun checksums(@TempDir tempDir: Path) = runBlocking {
    val file = tempDir.resolve("file.txt")
    file.write("test")
    val checksums = Checksums.compute(
      file,
      Checksums.Algorithm.SHA1,
      Checksums.Algorithm.SHA256,
      Checksums.Algorithm.SHA512,
      Checksums.Algorithm.MD5
    )
    assert(checksums.sha1sum == "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3") {
      "Unexpected SHA1 checksum '${checksums.sha1sum}'"
    }
    assert(checksums.sha256sum == "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08") {
      "Unexpected SHA256 checksum '${checksums.sha256sum}'"
    }
    assert(checksums.sha512sum == "ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff") {
      "Unexpected SHA512 checksum '${checksums.sha512sum}'"
    }
    assert(checksums.md5sum == "098f6bcd4621d373cade4e832627b4f6") {
      "Unexpected MD5 checksum '${checksums.md5sum}'"
    }
  }

  @Test
  fun `verify or write checksum file with file name`(@TempDir tempDir: Path) = runBlocking {
    val file = tempDir.resolve("file.txt")
    file.write("test")
    val checksums = Checksums.compute(file, Checksums.Algorithm.SHA256)

    val checksumFile = checksums.verifyOrWriteChecksumFile(Checksums.Algorithm.SHA256)

    assert("${checksums.sha256sum} *file.txt" == checksumFile.readText()) {
      "Unexpected checksum file content '${checksumFile.readText()}'"
    }
  }

  @Test
  fun `verify or write checksum file without file name`(@TempDir tempDir: Path) = runBlocking {
    val file = tempDir.resolve("file.txt")
    file.write("test")
    val checksums = Checksums.compute(file, Checksums.Algorithm.SHA256)

    val checksumFile = checksums.verifyOrWriteChecksumFile(Checksums.Algorithm.SHA256, withFileName = false)

    assert(checksums.sha256sum == checksumFile.readText()) {
      "Unexpected checksum file content '${checksumFile.readText()}'"
    }
  }
}
