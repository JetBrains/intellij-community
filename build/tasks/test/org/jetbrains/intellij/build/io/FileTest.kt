// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.io.write
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

private val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

class FileTest {
  @Test
  fun substitute_smoke(@TempDir tempDir: Path) {
    tempDir.resolve("in").write("""
      @sub1@
      some text@sub2@
      standalone@
    """.trimIndent())
    substituteTemplatePlaceholders(tempDir.resolve("in"), tempDir.resolve("out"),
    "@", listOf("sub1" to "1", "sub2" to "2"))
    Assertions.assertEquals("""
      1
      some text2
      standalone@
    """.trimIndent(), tempDir.resolve("out").readText())
  }

  @Test
  fun substitute_missing_placeholder(@TempDir tempDir: Path) {
    val inFile = tempDir.resolve("in")
    inFile.write("""
      @sub1@
      some text@sub2@
    """.trimIndent())

    try {
      substituteTemplatePlaceholders(inFile, tempDir.resolve("out"), "@", listOf("sub1" to "1"))
      Assertions.fail()
    } catch (e: IllegalStateException) {
      Assertions.assertEquals("Some template parameters were left unsubstituted in template file $inFile:\nline 2: some text@sub2@", e.message)
    }
  }

  @Test
  fun substitute_not_replaced_placeholder(@TempDir tempDir: Path) {
    val inFile = tempDir.resolve("in")
    inFile.write("some text")

    try {
      substituteTemplatePlaceholders(inFile, tempDir.resolve("out"), "@", listOf("missing_placeholder" to "1"))
      Assertions.fail()
    } catch (e: IllegalStateException) {
      Assertions.assertTrue(e.message!!.contains("Missing placeholders [@missing_placeholder@]"), e.message)
    }
  }

  @Test
  fun `copy file overwrites only when requested`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("source").also { it.write("new") }
    val targetDir = tempDir.resolve("target").createDirectories()
    val target = targetDir.resolve(source.fileName).also { it.write("old") }

    assertThrows<FileAlreadyExistsException> {
      copyFileToDir(source, targetDir)
    }

    copyFileToDir(source, targetDir, overwrite = true)
    Assertions.assertEquals("new", target.readText())
  }

  @Test
  fun `copy file overwrites a named target only when requested`(@TempDir tempDir: Path) {
    val source = tempDir.resolve("source").also { it.write("new") }
    val target = tempDir.resolve("target/nested/renamed").also {
      it.parent.createDirectories()
      it.write("old")
    }

    assertThrows<FileAlreadyExistsException> {
      copyFile(source, target)
    }

    copyFile(source, target, overwrite = true)
    Assertions.assertEquals("new", target.readText())
  }

  /**
   * A JCEF or JBR tree is a tree of macOS frameworks: `Frameworks/Chromium Embedded Framework.framework` is a relative
   * link to a sibling bundle holding the whole framework, so dereferencing it would break the layout and multiply its
   * size. The executable bit matters for the same reason - a helper an extracted archive marked `755` has to stay
   * runnable in the distribution.
   */
  @Test
  fun `copy directory reproduces symlinks as symlinks and keeps the executable bit`(@TempDir tempDir: Path) {
    assumeFalse(isWindows)

    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("payload.txt").write("payload")
    val executable = sourceDir.resolve("nested/run.sh")
    executable.parent.createDirectories()
    executable.write("#!/bin/sh\n")
    Files.setPosixFilePermissions(executable, Files.getPosixFilePermissions(executable) + PosixFilePermission.OWNER_EXECUTE)
    Files.createSymbolicLink(sourceDir.resolve("payload.link"), Path.of("payload.txt"))
    Files.createSymbolicLink(sourceDir.resolve("nested.link"), Path.of("nested"))

    val targetDir = tempDir.resolve("target/jcef")
    copyDir(sourceDir, targetDir)

    Assertions.assertEquals("payload", targetDir.resolve("payload.txt").readText())
    Assertions.assertTrue(Files.getPosixFilePermissions(targetDir.resolve("nested/run.sh")).contains(PosixFilePermission.OWNER_EXECUTE))
    assertSymbolicLinkTo(targetDir.resolve("payload.link"), "payload.txt")
    assertSymbolicLinkTo(targetDir.resolve("nested.link"), "nested")
  }

  @Test
  fun `copy directory replaces what an earlier layout left behind`(@TempDir tempDir: Path) {
    assumeFalse(isWindows)

    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("payload.txt").write("payload")
    Files.createSymbolicLink(sourceDir.resolve("payload.link"), Path.of("payload.txt"))

    val targetDir = tempDir.resolve("stale-layout/jcef").createDirectories()
    targetDir.resolve("payload.txt").write("stale")
    Files.createSymbolicLink(targetDir.resolve("payload.link"), Path.of("gone.txt"))

    copyDir(sourceDir, targetDir, overwrite = true)

    Assertions.assertEquals("payload", targetDir.resolve("payload.txt").readText())
    assertSymbolicLinkTo(targetDir.resolve("payload.link"), "payload.txt")
  }

  @Test
  fun `copy directory overwrites files and reports their targets`(@TempDir tempDir: Path) {
    val sourceDir = tempDir.resolve("source").createDirectories()
    val source = sourceDir.resolve("nested/native").also {
      it.parent.createDirectories()
      it.write("new")
    }
    val targetDir = tempDir.resolve("target").createDirectories()
    val target = targetDir.resolve(sourceDir.relativize(source)).also {
      it.parent.createDirectories()
      it.write("old")
    }

    val copied = copyDir(sourceDir, targetDir, overwrite = true)

    Assertions.assertEquals("new", target.readText())
    Assertions.assertEquals(listOf(target), copied)
  }

  private fun assertSymbolicLinkTo(file: Path, expectedTarget: String) {
    Assertions.assertTrue(Files.isSymbolicLink(file), "$file must be a symbolic link")
    Assertions.assertEquals(Path.of(expectedTarget), Files.readSymbolicLink(file))
  }
}
