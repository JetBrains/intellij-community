// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.test.Test

/**
 * The delay is deliberate. Every other test in this fixture runs in under a millisecond, which
 * rounds to the same `0.000` a broken shim would produce.
 */
class DurationTest {
  // E2eOutcomesTest asserts the report attributes at least most of these 50ms to this test.
  @Test
  fun reportsHowLongItRan(): Promise<JsAny?> = delay(50)
}

private fun delay(millis: Int): Promise<JsAny?> = js("new Promise((resolve) => setTimeout(resolve, millis))")
