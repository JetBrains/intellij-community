// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlin.test.Test
import kotlin.test.assertEquals

class FailingTest {
  @Test
  fun deliberateFailure() {
    assertEquals("expected", "actual", "this failure validates wasmjs_test failure reporting")
  }

  // A second failure: without fail fast both are reported, with fail fast only the first one.
  @Test
  fun secondDeliberateFailure() {
    assertEquals("expected", "actual", "this failure validates wasmjs_test fail fast")
  }
}
