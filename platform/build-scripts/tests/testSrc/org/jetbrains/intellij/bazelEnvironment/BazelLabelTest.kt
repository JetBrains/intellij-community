// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.bazelEnvironment

import org.jetbrains.intellij.bazelEnvironment.BazelLabel
import org.junit.Test
import org.junit.jupiter.api.Assertions

class BazelLabelTest {
  @Test
  fun smoke() {
    val label = BazelLabel.fromString("@repo//kotlin/kotlin-stdlib.Some:dir/kotlin-stdlib.jar")
    Assertions.assertEquals("repo", label.repo)
    Assertions.assertEquals("kotlin/kotlin-stdlib.Some", label.packageName)
    Assertions.assertEquals("dir/kotlin-stdlib.jar", label.target)
  }

  @Test
  fun noPackageName() {
    val label = BazelLabel.fromString("@repo//:kotlin-stdlib.jar")
    Assertions.assertEquals("repo", label.repo)
    Assertions.assertEquals("", label.packageName)
    Assertions.assertEquals("kotlin-stdlib.jar", label.target)
  }

  @Test
  fun noRepo() {
    val label = BazelLabel.fromString("//:kotlin-stdlib.jar")
    Assertions.assertEquals("", label.repo)
    Assertions.assertEquals("", label.packageName)
    Assertions.assertEquals("kotlin-stdlib.jar", label.target)
  }
}