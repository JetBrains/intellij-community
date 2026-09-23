// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class PathFromRootResolverTest {
  @JvmField
  @RegisterExtension
  val baseDir: TempDirectoryExtension = TempDirectoryExtension()

  @Test
  fun `returns the root name for the root itself`() {
    val root = baseDir.newVirtualDirectory("root")

    val path = PathFromRootResolver(listOf(root)).getPathFromRoot(root)

    assertThat(path).isEqualTo("root")
  }

  @Test
  fun `returns a qualified path for a descendant`() {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/dir/file.txt")

    val path = PathFromRootResolver(listOf(root)).getPathFromRoot(file)

    assertThat(path).isEqualTo("root/dir/file.txt")
  }

  @Test
  fun `uses the outer root regardless of root order`() {
    val outer = baseDir.newVirtualDirectory("outer")
    val nested = baseDir.newVirtualDirectory("outer/excluded/nested")
    // A cold file index can return the nested root first. Qualified name search must still use the outer root.
    val nestedFirstResolver = PathFromRootResolver(listOf(nested, outer))
    val outerFirstResolver = PathFromRootResolver(listOf(outer, nested))

    assertThat(nestedFirstResolver.getPathFromRoot(nested)).isEqualTo("outer/excluded/nested")
    assertThat(outerFirstResolver.getPathFromRoot(nested)).isEqualTo("outer/excluded/nested")
  }

  @Test
  fun `keeps only the outer root from a nested root chain`() {
    val outer = baseDir.newVirtualDirectory("outer")
    val middle = baseDir.newVirtualDirectory("outer/middle")
    val inner = baseDir.newVirtualDirectory("outer/middle/inner")
    val file = baseDir.newVirtualFile("outer/middle/inner/file.txt")
    val resolver = PathFromRootResolver(listOf(inner, outer, middle))

    val path = resolver.getPathFromRoot(file)

    assertThat(path).isEqualTo("outer/middle/inner/file.txt")
  }

  @Test
  fun `supports independent roots`() {
    val firstRoot = baseDir.newVirtualDirectory("first")
    val firstFile = baseDir.newVirtualFile("first/one.txt")
    val secondRoot = baseDir.newVirtualDirectory("second")
    val secondFile = baseDir.newVirtualFile("second/two.txt")
    val resolver = PathFromRootResolver(listOf(firstRoot, secondRoot))

    assertThat(resolver.getPathFromRoot(firstFile)).isEqualTo("first/one.txt")
    assertThat(resolver.getPathFromRoot(secondFile)).isEqualTo("second/two.txt")
  }

  @Test
  fun `keeps roots with common name prefixes independent`() {
    val root = baseDir.newVirtualDirectory("root")
    val similarRoot = baseDir.newVirtualDirectory("root-copy")
    val file = baseDir.newVirtualFile("root-copy/file.txt")

    val path = PathFromRootResolver(listOf(root, similarRoot)).getPathFromRoot(file)

    assertThat(path).isEqualTo("root-copy/file.txt")
  }

  @Test
  fun `returns null for a file outside the roots`() {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("other/file.txt")

    val path = PathFromRootResolver(listOf(root)).getPathFromRoot(file)

    assertThat(path).isNull()
  }

  @Test
  fun `returns null without roots`() {
    val file = baseDir.newVirtualFile("file.txt")

    val path = PathFromRootResolver(emptyList()).getPathFromRoot(file)

    assertThat(path).isNull()
  }
}
