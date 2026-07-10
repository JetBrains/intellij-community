// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class GitTest {
  @Test
  fun shouldDetectExecutable() {
    Assertions.assertTrue(Git.isExecutableGitMode("100755", "100755".length))
  }

  @Test
  fun shouldDetectNonExecutable() {
    Assertions.assertFalse(Git.isExecutableGitMode("100644", "100644".length))
  }
}
