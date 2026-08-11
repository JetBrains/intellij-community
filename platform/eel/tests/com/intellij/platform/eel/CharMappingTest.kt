// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.provider.utils.impl.ijentToLocal
import com.intellij.platform.eel.provider.utils.impl.localToIjent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(5, unit = TimeUnit.SECONDS)
internal class CharMappingTest {
  @OptIn(EelDelicateApi::class)
  @Test
  fun testPerformance() {
    val fileName = ":-/a".repeat(100)
    repeat(100_000) { // Make sure it is fast enough
      Assertions.assertTrue(localToIjent(fileName).isNotEmpty())
      Assertions.assertTrue(ijentToLocal(fileName).isNotEmpty())
    }
  }
}
