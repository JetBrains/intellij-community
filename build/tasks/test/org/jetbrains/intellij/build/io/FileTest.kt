// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.io.write
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

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
}
