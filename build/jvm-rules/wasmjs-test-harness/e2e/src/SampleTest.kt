// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTest {
  @Test
  fun passes() {
    // E2eOutcomesTest asserts this line reaches the report's <system-out>.
    println("printed by passes")
    assertEquals(4, 2 + 2)
  }

  @Test
  @Ignore
  fun ignored() {
    assertEquals(1, 2)
  }

  @Test
  fun configurationScriptRan() {
    assertEquals("configured", configuredGlobal())
  }
}

private fun configuredGlobal(): String = js("globalThis.__wasmjsE2eGlobal ?? 'missing'")
