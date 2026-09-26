// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource

@TestApplication
@ParameterizedClass
@EnumSource(PathFromRootResolverTest.InputType::class)
class PathFromRootResolverTest(private val inputType: InputType) {
  enum class InputType { PATH, VIRTUAL_FILE }

  @JvmField
  @RegisterExtension
  val baseDir: TempDirectoryExtension = TempDirectoryExtension()

  @Test
  fun `returns the root name for the root itself`() {
    val root = baseDir.newVirtualDirectory("root")

    val path = PathFromRootResolver(listOf(root)).resolve(root)

    assertThat(path).isEqualTo("root")
  }

  @Test
  fun `returns a qualified path for a descendant`() {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/dir/file.txt")

    val path = PathFromRootResolver(listOf(root)).resolve(file)

    assertThat(path).isEqualTo("root/dir/file.txt")
  }

  @Test
  fun `uses the outer root regardless of root order`() {
    val outer = baseDir.newVirtualDirectory("outer")
    val nested = baseDir.newVirtualDirectory("outer/excluded/nested")
    // A cold file index can return the nested root first. Qualified name search must still use the outer root.
    val nestedFirstResolver = PathFromRootResolver(listOf(nested, outer))
    val outerFirstResolver = PathFromRootResolver(listOf(outer, nested))

    assertThat(nestedFirstResolver.resolve(nested)).isEqualTo("outer/excluded/nested")
    assertThat(outerFirstResolver.resolve(nested)).isEqualTo("outer/excluded/nested")
  }

  @Test
  fun `keeps only the outer root from a nested root chain`() {
    val outer = baseDir.newVirtualDirectory("outer")
    val middle = baseDir.newVirtualDirectory("outer/middle")
    val inner = baseDir.newVirtualDirectory("outer/middle/inner")
    val file = baseDir.newVirtualFile("outer/middle/inner/file.txt")
    val resolver = PathFromRootResolver(listOf(inner, outer, middle))

    val path = resolver.resolve(file)

    assertThat(path).isEqualTo("outer/middle/inner/file.txt")
  }

  @Test
  fun `supports independent roots`() {
    val firstRoot = baseDir.newVirtualDirectory("first")
    val firstFile = baseDir.newVirtualFile("first/one.txt")
    val secondRoot = baseDir.newVirtualDirectory("second")
    val secondFile = baseDir.newVirtualFile("second/two.txt")
    val resolver = PathFromRootResolver(listOf(firstRoot, secondRoot))

    assertThat(resolver.resolve(firstFile)).isEqualTo("first/one.txt")
    assertThat(resolver.resolve(secondFile)).isEqualTo("second/two.txt")
  }

  @Test
  fun `keeps roots with common name prefixes independent`() {
    val root = baseDir.newVirtualDirectory("root")
    val similarRoot = baseDir.newVirtualDirectory("root-copy")
    val file = baseDir.newVirtualFile("root-copy/file.txt")

    val path = PathFromRootResolver(listOf(root, similarRoot)).resolve(file)

    assertThat(path).isEqualTo("root-copy/file.txt")
  }

  @Test
  fun `filters a nested root beside a similar independent root`() {
    val outer = baseDir.newVirtualDirectory("root")
    val nested = baseDir.newVirtualDirectory("root/dir")
    val similar = baseDir.newVirtualDirectory("root-copy")
    val file = baseDir.newVirtualFile("root/dir/file.txt")

    val path = PathFromRootResolver(listOf(similar, nested, outer)).resolve(file)

    assertThat(path).isEqualTo("root/dir/file.txt")
  }

  @Test
  fun `returns null for a file outside the roots`() {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("other/file.txt")

    val path = PathFromRootResolver(listOf(root)).resolve(file)

    assertThat(path).isNull()
  }

  @Test
  fun `returns null without roots`() {
    val file = baseDir.newVirtualFile("file.txt")

    val path = PathFromRootResolver(emptyList()).resolve(file)

    assertThat(path).isNull()
  }

  private fun PathFromRootResolver.resolve(file: VirtualFile): String? = when (inputType) {
    InputType.PATH -> getPathFromRoot(file.toNioPath())
    InputType.VIRTUAL_FILE -> getPathFromRoot(file)
  }
}
