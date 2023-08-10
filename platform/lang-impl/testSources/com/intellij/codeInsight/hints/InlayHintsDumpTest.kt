// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.testFramework.LightPlatformTestCase

class InlayHintsDumpTest : LightPlatformTestCase() {
  fun testExtractEntries() {
    assertEquals(listOf(5 to "foo", 8 to "bar"), InlayDumpUtil.extractEntries("01234/*<# foo #>*/567/*<# bar #>*/"))
  }
}