// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class MarkerSpecTest {
  @Test
  fun `with methods return the original spec for unchanged values`() {
    val spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = true, isStickingToRight = true)

    assertSame(spec, spec.withGreedyToLeft(false))
    assertSame(spec, spec.withGreedyToRight(true))
    assertSame(spec, spec.withStickyToRight(true))
  }

  @Test
  fun `with methods update one property`() {
    val spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = true, isStickingToRight = true)

    assertEquals(
      MarkerSpec(isGreedyToLeft = true, isGreedyToRight = true, isStickingToRight = true),
      spec.withGreedyToLeft(true),
    )
    assertEquals(
      MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false, isStickingToRight = true),
      spec.withGreedyToRight(false),
    )
    assertEquals(
      MarkerSpec(isGreedyToLeft = false, isGreedyToRight = true, isStickingToRight = false),
      spec.withStickyToRight(false),
    )
  }
}
