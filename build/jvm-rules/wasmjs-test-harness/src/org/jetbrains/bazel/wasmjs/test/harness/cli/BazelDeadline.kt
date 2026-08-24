// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.cli

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The in-harness deadline derived from `TEST_TIMEOUT`: end the run early enough to tear the browser
 * down and still write the reports before Bazel sends SIGTERM.
 */
internal fun softDeadline(timeout: Duration): Duration {
  val softDeadline = timeout - TEARDOWN_GRACE
  require(softDeadline.isPositive()) { "test timeout cannot be under $TEARDOWN_GRACE" }
  return softDeadline
}

private val TEARDOWN_GRACE = 15.seconds
