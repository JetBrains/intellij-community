// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

class TestRunExecutionTestState {
  @Test
  fun `soft deadline reserves the standard teardown grace for normal timeouts`() {
    assertEquals(285.seconds, softDeadline(300.seconds))
    assertEquals(45.seconds, softDeadline(60.seconds))
  }

  @Test
  fun `soft deadline stays positive for timeouts at or below the teardown grace`() {
    assertEquals(15.seconds - 15.seconds / 4, softDeadline(15.seconds))
    assertEquals(4.seconds - 1.seconds, softDeadline(4.seconds))
    assertTrue(softDeadline(1.seconds).isPositive())
  }

  @Test
  fun `a relative browser profile dir resolves under the test tmp dir`() {
    assertEquals(
      Path("/tmp/test/browser-profile"),
      resolveBrowserProfileDir("browser-profile", tmpDir = Path("/tmp/test")),
    )
  }

  @Test
  fun `an absolute browser profile dir is used as-is`() {
    assertEquals(
      Path("/var/profiles/wasmjs"),
      resolveBrowserProfileDir("/var/profiles/wasmjs", tmpDir = Path("/tmp/test")),
    )
  }

  @Test
  fun `a missing browser binary is rejected as an invocation error`() {
    val fixtureDir = Files.createTempDirectory("wasmjs-test")
    val browserFlagfile = fixtureDir.resolve("browser.flagfile").also { it.writeText("--headless\n") }
    val result = BazelRunnerInvocation.parse(listOf(
      "--browser-binary=${fixtureDir.resolve("missing-headless-shell")}",
      "--browser-flagfile=$browserFlagfile",
      "--browser-profile-dir=${fixtureDir.resolve("browser-profile")}",
      "--static-content-dir=static",
      "--entrypoint=m-js/m.mjs",
    ), BazelTestEnvironment.from(emptyMap()))

    val error = when (result) {
      is BazelInvocationParseResult.Valid -> throw AssertionError("expected an invalid invocation, got: ${result.invocation}")
      is BazelInvocationParseResult.Invalid -> result.error
    }
    assertTrue(error, error.contains("browser binary does not exist"))
  }

  @Test
  fun `the profile dir placeholder is substituted into browser arguments`() {
    assertEquals(
      listOf("--headless", "--user-data-dir=/tmp/test/browser-profile", "about:blank"),
      substituteBrowserProfileDir(
        listOf("--headless", "--user-data-dir=\${BROWSER_PROFILE_DIR}", "about:blank"),
        profileDir = Path("/tmp/test/browser-profile"),
      ),
    )
  }
}
