// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InlayHintsDumpTest {
  @Test
  fun testExtractEntries() {
    assertEquals(listOf(5 to "foo", 8 to "bar"), InlayDumpUtil.extractEntries("01234/*<# foo #>*/567/*<# bar #>*/"))
  }

  @Test
  fun testInlayMayContainOctothorpe() {
    assertEquals(listOf(5 to "foo#bar"), InlayDumpUtil.extractEntries("01234/*<# foo#bar #>*/567"))
  }
}