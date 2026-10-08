// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
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

  // The static root is addressed through the marker file the rule writes at its top level, so the
  // fixture carries one too.
  private val staticRoot = fixtureDir.resolve("static").also { it.createDirectories() }
  private val staticRootMarker = staticRoot.resolve(".static-root").also { it.writeText("") }

  private val minimalArguments = listOf(
    "--browser-binary=$browserBinary",
    "--browser-flagfile=$browserFlagfile",
    "--browser-profile-dir=$profileDir",
    "--static-content-marker=$staticRootMarker",
    "--entrypoint=module-js/module.mjs",
    "--mocha-script=_harness/mocha.js",
    "--kotlin-test-runner-script=_harness/kotlin-test-mocha-browser-runner.js",
  )

  @Test
  fun `a minimal invocation parses with the browser command assembled and the default timeout`() {
    val invocation = parseValid(minimalArguments)

    assertEquals(
      listOf(browserBinary.toString(), "--headless", "--user-data-dir=$profileDir"),
      invocation.browserCommand,
    )
    assertEquals(staticRoot, invocation.staticContentDir)
    assertEquals("module-js/module.mjs", invocation.entrypoint)
    assertEquals("_harness/mocha.js", invocation.mochaScript)
    assertEquals("_harness/kotlin-test-mocha-browser-runner.js", invocation.kotlinTestRunnerScript)
    assertEquals(30.seconds, invocation.browserSetupTimeout)
  }

  @Test
  fun `the browser and test runner options are required exactly once`() {
    listOf(
      "--browser-binary", "--browser-flagfile", "--browser-profile-dir",
      "--mocha-script", "--kotlin-test-runner-script",
    ).forEach { option ->
      assertTrue(parseError(minimalArguments.filterNot { it.startsWith("$option=") }).contains(option))
      assertTrue(parseError(minimalArguments + "$option=twice").contains(option))
    }
  }

  @Test
  fun `the browser setup timeout is overridable`() {
    val invocation = parseValid(minimalArguments + "--browser-setup-timeout-ms=180000")

    assertEquals(180.seconds, invocation.browserSetupTimeout)
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
      "--browser-setup-timeout-ms=1000",
      "--browser-setup-timeout-ms=2000",
    ))

    assertTrue(error, error.contains("--browser-setup-timeout-ms"))
  }

  @Test
  fun `unknown options are rejected`() {
    // An unknown phase is an unknown option: there is no phase name left to look up at runtime. The
    // retired options are listed too — page load and the run are bounded by the Bazel deadline, and
    // the page reports the end of the run, so no flag sets them.
    listOf(
      "--frobnicate=yes",
      "--quiescence-timeout-ms=3000",
      "--test-setup-timeout-ms=60000",
      "--test-completion-grace-ms=3000",
      "--test-completion-grace-period-ms=3000",
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
