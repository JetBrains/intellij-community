// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.io.write
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class FileSetTest {
  @Test
  fun includeAll(@TempDir tempDir: Path) {
    createExample(tempDir)
    assertPaths(
      tempDir,
      listOf("abc1/file1", "abc1/file2", "abc2/file1", "abc2/file2"),
      FileSet(tempDir).includeAll().enumerate(),
    )
  }

  @Test
  fun double_star_matches_empty(@TempDir tempDir: Path) {
    createExample(tempDir)
    assertPaths(
      tempDir,
      listOf("abc1/file1", "abc1/file2"),
      FileSet(tempDir).include("**/abc1/**").enumerate(),
    )
  }

  @Test
  fun copyToDir_preserve_structure(@TempDir tempDir: Path) {
    createExample(tempDir)
    val target = tempDir.resolve("target")
    FileSet(tempDir).include("**/file1**").copyToDir(target)
    assertPaths(
      target,
      listOf("abc1/file1", "abc2/file1"),
      FileSet(target).includeAll().enumerate(),
    )
  }

  @Test
  fun isEmpty(@TempDir tempDir: Path) {
    createExample(tempDir)
    // isEmpty accepts even unmatched patterns
    assertTrue(FileSet(tempDir).include("abc3**").isEmpty())
    assertFalse(FileSet(tempDir).include("abc1**/**").exclude("no-file").isEmpty())
    assertTrue(FileSet(tempDir).include("abc1**").exclude("**/file*").isEmpty())
  }

  @Test
  fun delete(@TempDir tempDir: Path) {
    createExample(tempDir)
    FileSet(tempDir).includeAll().delete()
    assertTrue(tempDir.resolve("abc1").isDirectory())
    assertFalse(tempDir.resolve("abc1/file1").exists())
  }

  @Test
  fun includeSome(@TempDir tempDir: Path) {
    createExample(tempDir)
    assertPaths(
      tempDir,
      listOf("abc1/file1", "abc2/file1", "abc2/file2"),
      FileSet(tempDir).include("**/file1").include("abc2/file2").enumerate(),
    )
  }

  @Test
  fun no_match_just_by_directory_name(@TempDir tempDir: Path) {
    try {
      createExample(tempDir)
      FileSet(tempDir).include("abc1").enumerate()
      fail()
    } catch (e: java.lang.IllegalStateException) {
      assertTrue(e.message!!.contains("include patterns were not matched"), e.message)
    }
  }

  @Test
  fun assert_no_include_patterns(@TempDir tempDir: Path) {
    try {
      FileSet(tempDir).exclude("**").enumerate()
      fail()
    } catch (e: java.lang.IllegalStateException) {
      assertTrue(e.message!!.contains("No include patterns"), e.message)
    }
  }

  @Test
  fun assert_unused_exclude_pattern(@TempDir tempDir: Path) {
    try {
      createExample(tempDir)
      FileSet(tempDir).include("abc1**/**").exclude("abc2/file2").enumerate()
      fail()
    } catch (e: java.lang.IllegalStateException) {
      assertTrue(e.message!!.contains("exclude patterns were not matched"), e.message)
    }
  }

  @Test
  fun assert_unused_include_pattern(@TempDir tempDir: Path) {
    try {
      createExample(tempDir)
      FileSet(tempDir).include("abc1**").include("abc3**").enumerate()
      fail()
    } catch (e: java.lang.IllegalStateException) {
      assertTrue(e.message!!.contains("include patterns were not matched"), e.message)
    }
  }

  @Test
  fun no_assert_unused_include_pattern_when_covered_by_another(@TempDir tempDir: Path) {
    createExample(tempDir)
    FileSet(tempDir).includeAll().include("abc1**/**").enumerate()
  }

  private fun assertPaths(root: Path, expected: List<String>, actual: List<Path>) {
    val expectedPaths = expected.map { root.resolve(it) }.sorted().joinToString("", postfix = "\n")
    val actualPaths = actual.sorted().joinToString("", postfix = "\n")

    Assertions.assertEquals(expectedPaths, actualPaths)
  }

  private fun createExample(root: Path) {
    val abc1 = root.resolve("abc1")
    val abc2 = root.resolve("abc2")
    abc1.resolve("file1").write("")
    abc1.resolve("file2").write("")
    abc2.resolve("file1").write("")
    abc2.resolve("file2").write("")
  }
}
