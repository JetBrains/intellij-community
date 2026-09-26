// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlin.test.Test

// Exercised by E2eOutcomesTest: the test itself passes, but the callback it detached throws after
// it — an uncaught page exception no test reported, which must still fail the run through the
// harness's synthetic uncaught-exception case.
class ThrowingTest {
  @Test
  fun detachesAnUncaughtException() {
    scheduleThrow()
  }
}

private fun scheduleThrow(): Unit = js("""setTimeout(() => { throw new Error("detached boom"); }, 0)""")
