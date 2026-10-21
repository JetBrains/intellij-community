// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
class NioRootsTest {
  @TempDir
  lateinit var root: Path

  private fun descriptor(): FileChooserDescriptor = FileChooserDescriptor(true, true, false, false)

  @Test
  fun `getNioRoots returns the path roots of the descriptor`() {
    val one = root.resolve("one")
    val two = root.resolve("two")
    val descriptor = descriptor().withPathRoots(one, two)
    assertThat(NioFileChooserUtil.getNioRoots(descriptor)).containsExactly(one, two)
  }

  @Test
  fun `getNioRoots removes the duplicates`() {
    val one = root.resolve("one")
    val descriptor = descriptor().withPathRoots(one, one)
    assertThat(NioFileChooserUtil.getNioRoots(descriptor)).containsExactly(one)
  }

  @Test
  fun `getNioRoots is empty without roots`() {
    assertThat(NioFileChooserUtil.getNioRoots(descriptor())).isEmpty()
  }

  @Test
  fun `the copy constructor keeps the path roots`() {
    val one = root.resolve("one")
    val descriptor = descriptor().withPathRoots(one)
    val copy = FileChooserDescriptor(descriptor)
    assertThat(copy.pathRoots).containsExactly(one)
  }
}
