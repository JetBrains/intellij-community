// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.test.Test

// Exercised by E2eOutcomesTest: the harness deadline must error this test and still write
// the reports — an interrupted run must never pass as green.
class HangingTest {
  @Test
  fun hangsForever(): Promise<JsAny?> = neverSettles()
}

private fun neverSettles(): Promise<JsAny?> = js("new Promise(() => {})")
