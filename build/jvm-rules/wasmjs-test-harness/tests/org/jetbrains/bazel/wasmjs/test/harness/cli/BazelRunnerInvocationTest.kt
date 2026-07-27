// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BazelRunnerInvocationTest {
  // No runfiles and no TEST_TMPDIR: paths resolve through the filesystem only, deterministically.
  private val environment = BazelTestEnvironment.from(emptyMap())

  // parse prepares the run (it reads the browser flagfile and checks the binary), so the browser
  // fixture must exist on disk.
  private val fixtureDir = Files.createTempDirectory("wasmjs-invocation-test")
  private val browserBinary = fixtureDir.resolve("headless_shell").also { it.writeText("#!/bin/sh") }
  private val browserFlagfile = fixtureDir.resolve("browser.flagfile").also { flagfile ->
    flagfile.writeText("--headless\n--user-data-dir=\${BROWSER_PROFILE_DIR}\n")
  }
  private val profileDir = fixtureDir.resolve("browser-profile")

  private val minimalArguments = listOf(
    "--browser-binary=$browserBinary",
    "--browser-flagfile=$browserFlagfile",
    "--browser-profile-dir=$profileDir",
    "--static-content-dir=static",
    "--entrypoint=module-js/module.mjs",
  )

  @Test
  fun `a minimal invocation parses with the browser command assembled and the default timeouts`() {
    val invocation = parseValid(minimalArguments)

    assertEquals(
      listOf(browserBinary.toString(), "--headless", "--user-data-dir=$profileDir"),
      invocation.browserCommand,
    )
    assertEquals(Path("static"), invocation.staticContentDir)
    assertEquals("module-js/module.mjs", invocation.entrypoint)
    assertEquals(30.seconds, invocation.browserSetupTimeout)
    assertEquals(3.seconds, invocation.testCompletionGracePeriod)
  }

  @Test
  fun `the browser options are required exactly once`() {
    listOf("--browser-binary", "--browser-flagfile", "--browser-profile-dir").forEach { option ->
      assertTrue(parseError(minimalArguments.filterNot { it.startsWith("$option=") }).contains(option))
      assertTrue(parseError(minimalArguments + "$option=twice").contains(option))
    }
  }

  @Test
  fun `timeout options override only the named phases`() {
    val invocation = parseValid(minimalArguments + "--browser-setup-timeout-ms=180000")

    assertEquals(180.seconds, invocation.browserSetupTimeout)
    // Untouched by the flagfile, so still the default.
    assertEquals(3.seconds, invocation.testCompletionGracePeriod)
  }

  @Test
  fun `every timeout phase is overridable`() {
    val invocation = parseValid(minimalArguments + listOf(
      "--browser-setup-timeout-ms=11000",
      // Sub-second: the whole point of the millisecond unit is that the grace period can go below 1s.
      "--test-completion-grace-period-ms=1500",
    ))

    assertEquals(11.seconds, invocation.browserSetupTimeout)
    assertEquals(1500.milliseconds, invocation.testCompletionGracePeriod)
  }

  @Test
  fun `invalid timeout options are rejected`() {
    listOf(
      "--browser-setup-timeout-ms=abc",
      "--browser-setup-timeout-ms=0",
      "--browser-setup-timeout-ms=-3",
      "--browser-setup-timeout-ms",
    ).forEach { option ->
      assertTrue(parseError(minimalArguments + option).contains(option.substringBefore('=')))
    }
  }

  @Test
  fun `a timeout option is rejected when repeated`() {
    val error = parseError(minimalArguments + listOf(
      "--test-completion-grace-period-ms=1000",
      "--test-completion-grace-period-ms=2000",
    ))

    assertTrue(error, error.contains("--test-completion-grace-period-ms"))
  }

  @Test
  fun `unknown options are rejected`() {
    // An unknown phase is an unknown option: there is no phase name left to look up at runtime. The
    // retired options are listed too — page load is bounded by the Bazel deadline, not by a flag.
    listOf(
      "--frobnicate=yes",
      "--quiescence-timeout-ms=3000",
      "--test-setup-timeout-ms=60000",
      "--test-completion-grace-ms=3000",
    ).forEach { option ->
      assertTrue(parseError(minimalArguments + option).contains(option.substringBefore('=')))
    }
  }

  @Test
  fun `an unreadable flagfile is an error, not an exception`() {
    val error = parseError(listOf("--flagfile=${fixtureDir.resolve("missing.flagfile")}"))

    assertTrue(error, error.contains("cannot prepare the run invocation"))
  }

  @Test
  fun `a bare start outside bazel test is rejected`() {
    assertTrue(parseError(emptyList()).contains("TEST_BINARY"))
  }

  private fun parseValid(arguments: List<String>): BazelRunnerInvocation =
    when (val result = BazelRunnerInvocation.parse(arguments, environment)) {
      is BazelInvocationParseResult.Valid -> result.invocation
      is BazelInvocationParseResult.Invalid -> throw AssertionError("expected a valid invocation, got: ${result.error}")
    }

  private fun parseError(arguments: List<String>): String =
    when (val result = BazelRunnerInvocation.parse(arguments, environment)) {
      is BazelInvocationParseResult.Valid -> throw AssertionError("expected an invalid invocation, got: ${result.invocation}")
      is BazelInvocationParseResult.Invalid -> result.error
    }
}
