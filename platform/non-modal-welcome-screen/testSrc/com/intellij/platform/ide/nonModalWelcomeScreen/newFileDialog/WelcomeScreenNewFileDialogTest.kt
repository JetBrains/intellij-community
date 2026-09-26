// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.FileSystems

/**
 * Tests for [WelcomeScreenNewFileDialog.normalizeDirectoryPath].
 *
 * The method ensures paths use forward slashes, which is required by [DirectoryUtil.mkdirs].
 *
 * **Note:** Windows-specific tests use [@EnabledOnOs] and will be skipped on other platforms.
 * They will run on Windows agents in TeamCity.
 *
 * @see IJPL-217109
 */
class WelcomeScreenNewFileDialogTest {

  /**
   * Regression test for IJPL-217109: [DirectoryUtil.mkdirs] requires forward slashes.
   * Simulates user input with platform-native separators (backslashes on Windows).
   */
  @Test
  fun `normalizeDirectoryPath uses forward slashes`() {
    val separator = FileSystems.getDefault().separator
    val userInput = listOf("Users", "test", "NewProject").joinToString(separator)

    val normalized = WelcomeScreenNewFileDialog.normalizeDirectoryPath(userInput)

    assertFalse(normalized.contains("\\"),
                "Path for DirectoryUtil must not contain backslashes: $normalized")
  }

  @Test
  fun `normalizeDirectoryPath resolves parent directory references`() {
    val pathWithDots = "home/user/../user/project"

    val normalized = WelcomeScreenNewFileDialog.normalizeDirectoryPath(pathWithDots)

    assertFalse(normalized.contains(".."), "Normalized path should not contain '..': $normalized")
    assertEquals("home/user/project", normalized)
  }

  @Test
  fun `normalizeDirectoryPath resolves current directory references`() {
    val pathWithDot = "home/./user/./project"

    val normalized = WelcomeScreenNewFileDialog.normalizeDirectoryPath(pathWithDot)

    assertEquals("home/user/project", normalized)
  }

  /**
   * Regression test for IJPL-217109: Windows backslash paths must be converted to forward slashes.
   * This test runs ONLY on Windows agents in TeamCity.
   */
  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `normalizeDirectoryPath converts Windows backslashes to forward slashes`() {
    val windowsPath = "C:\\Users\\test\\project"

    val normalized = WelcomeScreenNewFileDialog.normalizeDirectoryPath(windowsPath)

    assertEquals("C:/Users/test/project", normalized)
  }

  /**
   * Tests Windows paths with mixed separators (common when copy-pasting paths).
   * This test runs ONLY on Windows agents in TeamCity.
   */
  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `normalizeDirectoryPath handles Windows mixed separators`() {
    val mixedPath = "C:/Users\\test/project\\src"

    val normalized = WelcomeScreenNewFileDialog.normalizeDirectoryPath(mixedPath)

    assertEquals("C:/Users/test/project/src", normalized)
    assertFalse(normalized.contains("\\"), "Path should not contain backslashes")
  }

  /**
   * Tests that trailing backslashes are handled correctly on Windows.
   * This test runs ONLY on Windows agents in TeamCity.
   */
  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `normalizeDirectoryPath handles Windows trailing separators`() {
    val pathWithTrailing = "C:\\Users\\test\\"

    val normalized = WelcomeScreenNewFileDialog.normalizeDirectoryPath(pathWithTrailing)

    assertEquals("C:/Users/test", normalized)
  }
}
