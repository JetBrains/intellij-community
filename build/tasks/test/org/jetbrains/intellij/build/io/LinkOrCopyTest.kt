// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission

/**
 * [linkOrCopyFile] and [linkOrCopyDir] put an immutable cache entry into a dev run directory, so what they must
 * preserve is exactly what an extracted archive can hold: shared bytes for regular files, and links for links.
 */
private val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

class LinkOrCopyTest {
  @TempDir
  lateinit var temp: Path

  @Test
  fun `linkOrCopyDir shares bytes with the source and reproduces symlinks as symlinks`() {
    Assumptions.assumeFalse(isWindows)

    val sourceDir = createCacheEntry()
    val targetDir = Files.createDirectory(temp.resolve("layout")).resolve("jcef")
    linkOrCopyDir(sourceDir, targetDir)

    Assertions.assertEquals("payload", Files.readString(targetDir.resolve("payload.txt")))
    Assertions.assertEquals(
      fileKey(sourceDir.resolve("payload.txt")),
      fileKey(targetDir.resolve("payload.txt")),
      "a regular file must share its bytes with the cache entry instead of being copied",
    )
    Assertions.assertTrue(Files.getPosixFilePermissions(targetDir.resolve("nested/run.sh")).contains(PosixFilePermission.OWNER_EXECUTE))

    // a dereferenced link would arrive here as a regular file - which both breaks a framework layout and multiplies its size
    assertSymbolicLinkTo(targetDir.resolve("payload.link"), "payload.txt")
    assertSymbolicLinkTo(targetDir.resolve("nested.link"), "nested")
  }

  @Test
  fun `linkOrCopyDir replaces what an earlier layout left behind`() {
    Assumptions.assumeFalse(isWindows)

    val sourceDir = createCacheEntry()
    val targetDir = Files.createDirectory(temp.resolve("stale-layout")).resolve("jcef")
    Files.createDirectories(targetDir.resolve("nested"))
    Files.writeString(targetDir.resolve("payload.txt"), "stale")
    Files.createSymbolicLink(targetDir.resolve("payload.link"), Path.of("gone.txt"))
    Files.createSymbolicLink(targetDir.resolve("nested.link"), Path.of("gone"))

    linkOrCopyDir(sourceDir, targetDir)

    Assertions.assertEquals("payload", Files.readString(targetDir.resolve("payload.txt")))
    Assertions.assertEquals(fileKey(sourceDir.resolve("payload.txt")), fileKey(targetDir.resolve("payload.txt")))
    assertSymbolicLinkTo(targetDir.resolve("payload.link"), "payload.txt")
    assertSymbolicLinkTo(targetDir.resolve("nested.link"), "nested")
  }

  @Test
  fun `linkOrCopyFile creates the target parent and keeps a symlink a symlink`() {
    Assumptions.assumeFalse(isWindows)

    val sourceDir = createCacheEntry()
    val targetDir = Files.createDirectory(temp.resolve("single-file-layout"))

    linkOrCopyFile(sourceDir.resolve("payload.txt"), targetDir.resolve("lib/maven3/payload.txt"))
    Assertions.assertEquals(fileKey(sourceDir.resolve("payload.txt")), fileKey(targetDir.resolve("lib/maven3/payload.txt")))

    linkOrCopyFile(sourceDir.resolve("payload.link"), targetDir.resolve("lib/maven3/payload.link"))
    assertSymbolicLinkTo(targetDir.resolve("lib/maven3/payload.link"), "payload.txt")
  }

  private fun createCacheEntry(): Path {
    val sourceDir = Files.createDirectory(temp.resolve("cache-entry-${System.nanoTime()}"))
    Files.writeString(sourceDir.resolve("payload.txt"), "payload")
    val nested = Files.createDirectories(sourceDir.resolve("nested"))
    val executable = nested.resolve("run.sh")
    Files.writeString(executable, "#!/bin/sh\n")
    Files.setPosixFilePermissions(executable, Files.getPosixFilePermissions(executable) + PosixFilePermission.OWNER_EXECUTE)
    Files.createSymbolicLink(sourceDir.resolve("payload.link"), Path.of("payload.txt"))
    Files.createSymbolicLink(sourceDir.resolve("nested.link"), Path.of("nested"))
    return sourceDir
  }

  private fun assertSymbolicLinkTo(file: Path, expectedTarget: String) {
    Assertions.assertTrue(Files.isSymbolicLink(file), "$file must be a symbolic link")
    Assertions.assertEquals(Path.of(expectedTarget), Files.readSymbolicLink(file))
  }

  private fun fileKey(file: Path): Any {
    return checkNotNull(Files.readAttributes(file, BasicFileAttributes::class.java).fileKey()) {
      "no file key for '$file' - this filesystem cannot tell a hardlink from a copy"
    }
  }
}
