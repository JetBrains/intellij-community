// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The second computation of the ultimate half must find the foreign community plugins of the first computation, see
 * [requireForeignFixpoint].
 */
class DevDistForeignFixpointTest {
  @Test
  fun `the same set of foreign plugins passes`() {
    assertThatCode { requireForeignFixpoint(foreign = setOf("intellij.a", "intellij.b"), second = setOf("intellij.b", "intellij.a")) }
      .doesNotThrowAnyException()
  }

  @Test
  fun `a changed set fails and names the plugins that join and leave it`() {
    assertThatThrownBy { requireForeignFixpoint(foreign = setOf("intellij.a", "intellij.b"), second = setOf("intellij.c", "intellij.a")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("The second computation of the ultimate half changes the foreign community plugins. It adds [intellij.c] and removes [intellij.b].")
  }
}
