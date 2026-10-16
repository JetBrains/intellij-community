// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.unit

import com.intellij.platform.lsp.impl.LspGlobMatcher
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationPattern
import org.eclipse.lsp4j.FileOperationPatternKind
import org.eclipse.lsp4j.FileOperationPatternOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LspGlobMatcherTest {
  private val globMatcher = LspGlobMatcher()

  private fun fileOperationFilter(
    glob: String,
    matches: String? = null,
    ignoreCase: Boolean = false,
    scheme: String? = null,
  ): FileOperationFilter {
    val pattern = FileOperationPattern(glob).apply {
      this.matches = matches
      if (ignoreCase) options = FileOperationPatternOptions(true)
    }
    return FileOperationFilter(pattern, scheme)
  }

  @Test
  fun `pattern without kind matches files and folders`() {
    val filter = fileOperationFilter("**/src/**")
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/src/a.ts", "file", isDirectory = false, filter))
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/src/lib", "file", isDirectory = true, filter))
    assertFalse(globMatcher.pathMatchesFileOperationFilter("/project/test/a.ts", "file", isDirectory = false, filter))
  }

  @Test
  fun `file kind does not match a folder`() {
    val filter = fileOperationFilter("**/*.ts", matches = FileOperationPatternKind.File)
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "file", isDirectory = false, filter))
    assertFalse(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "file", isDirectory = true, filter))
  }

  @Test
  fun `folder kind does not match a file`() {
    val filter = fileOperationFilter("**/lib", matches = FileOperationPatternKind.Folder)
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/lib", "file", isDirectory = true, filter))
    assertFalse(globMatcher.pathMatchesFileOperationFilter("/project/lib", "file", isDirectory = false, filter))
  }

  @Test
  fun `unknown kind matches nothing`() {
    val filter = fileOperationFilter("**/*.ts", matches = "symlink")
    assertFalse(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "file", isDirectory = false, filter))
  }

  @Test
  fun `scheme must match`() {
    val filter = fileOperationFilter("**/*.ts", scheme = "untitled")
    assertFalse(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "file", isDirectory = false, filter))
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "untitled", isDirectory = false, filter))
  }

  @Test
  fun `braces match alternatives`() {
    val filter = fileOperationFilter("**/*.{ts,js}")
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "file", isDirectory = false, filter))
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/a.js", "file", isDirectory = false, filter))
    assertFalse(globMatcher.pathMatchesFileOperationFilter("/project/a.css", "file", isDirectory = false, filter))
  }

  @Test
  fun `invalid glob matches nothing`() {
    assertFalse(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "file", isDirectory = false, fileOperationFilter("**/[")))
  }

  @Test
  fun `ignoreCase matches any case`() {
    val filter = fileOperationFilter("**/*.TS", ignoreCase = true)
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/a.ts", "file", isDirectory = false, filter))
    assertTrue(globMatcher.pathMatchesFileOperationFilter("/project/B.Ts", "file", isDirectory = false, filter))
  }
}
