// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class GitTest {
  @Test
  fun shouldDetectExecutable() {
    val entry = Git.Entry("x", "100755")
    Assertions.assertTrue(entry.isExecutable)
  }

  @Test
  fun shouldDetectNonExecutable() {
    val entry = Git.Entry("x", "100644")
    Assertions.assertFalse(entry.isExecutable)
  }
}
