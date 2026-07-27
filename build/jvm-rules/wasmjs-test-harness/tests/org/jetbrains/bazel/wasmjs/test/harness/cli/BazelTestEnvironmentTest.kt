// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

class BazelTestEnvironmentTest {
  @Test
  fun `the default flagfile is derived from TEST_WORKSPACE and TEST_BINARY`() {
    val environment = BazelTestEnvironment.from(mapOf(
      "TEST_WORKSPACE" to "rules_jvm",
      "TEST_BINARY" to "wasmjs-test-harness/e2e/e2e",
    ))

    assertEquals("rules_jvm/wasmjs-test-harness/e2e/e2e.flagfile", environment.defaultFlagfileRlocationPath())
  }

  @Test
  fun `the windows launcher exe suffix is stripped from the flagfile name`() {
    val environment = BazelTestEnvironment.from(mapOf(
      "TEST_WORKSPACE" to "rules_jvm",
      "TEST_BINARY" to "wasmjs-test-harness/e2e/e2e.exe",
    ))

    assertEquals("rules_jvm/wasmjs-test-harness/e2e/e2e.flagfile", environment.defaultFlagfileRlocationPath())
  }

  @Test
  fun `a TEST_BINARY traversing into a sibling repository is normalized`() {
    val environment = BazelTestEnvironment.from(mapOf(
      "TEST_WORKSPACE" to "_main",
      "TEST_BINARY" to "../rules_jvm+/wasmjs-test-harness/e2e/e2e",
    ))

    assertEquals("rules_jvm+/wasmjs-test-harness/e2e/e2e.flagfile", environment.defaultFlagfileRlocationPath())
  }

  @Test
  fun `there is no default flagfile outside bazel test`() {
    assertNull(BazelTestEnvironment.from(emptyMap()).defaultFlagfileRlocationPath())
    assertNull(BazelTestEnvironment.from(mapOf("TEST_BINARY" to "some/test")).defaultFlagfileRlocationPath())
    assertNull(BazelTestEnvironment.from(mapOf("TEST_WORKSPACE" to "_main")).defaultFlagfileRlocationPath())
  }

  @Test
  fun `requested sharding is an unsupported feature`() {
    val sharded = BazelTestEnvironment.from(mapOf("TEST_TOTAL_SHARDS" to "2"))
    assertTrue(sharded.unsupportedFeatures.any { it.contains("shard") })

    // 0 is how Bazel says sharding is disabled.
    assertEquals(emptyList<String>(), BazelTestEnvironment.from(mapOf("TEST_TOTAL_SHARDS" to "0")).unsupportedFeatures)
  }

  @Test
  fun `fail fast is an unsupported feature`() {
    val failFast = BazelTestEnvironment.from(mapOf("TESTBRIDGE_TEST_RUNNER_FAIL_FAST" to "1"))
    assertTrue(failFast.unsupportedFeatures.single().contains("fail_fast"))

    assertEquals(emptyList<String>(), BazelTestEnvironment.from(mapOf("TESTBRIDGE_TEST_RUNNER_FAIL_FAST" to "0")).unsupportedFeatures)
  }

  @Test
  fun `TEST_RANDOM_SEED is only a warning`() {
    val seeded = BazelTestEnvironment.from(mapOf("TEST_RANDOM_SEED" to "42"))

    assertTrue(seeded.warnings.single().contains("TEST_RANDOM_SEED"))
    assertEquals(emptyList<String>(), seeded.unsupportedFeatures)
    assertEquals(emptyList<String>(), BazelTestEnvironment.from(emptyMap()).warnings)
  }

  @Test
  fun `test filters split on commas and drop blanks`() {
    assertEquals(
      listOf("a.B", "c.D"),
      BazelTestEnvironment.from(mapOf("TESTBRIDGE_TEST_ONLY" to "a.B,,c.D")).testFilters,
    )
    assertEquals(emptyList<String>(), BazelTestEnvironment.from(emptyMap()).testFilters)
  }

  @Test
  fun `a non-numeric TEST_TIMEOUT leaves the deadline unset`() {
    assertNull(BazelTestEnvironment.from(mapOf("TEST_TIMEOUT" to "soon")).timeout)
    assertEquals(300.seconds, BazelTestEnvironment.from(mapOf("TEST_TIMEOUT" to "300")).timeout)
  }

  @Test
  fun `an absolute runner path resolves to itself`() {
    val file = Files.createTempDirectory("runner-path").resolve("browser.flagfile")

    assertEquals(file, resolveRunnerPath(file.toString(), BazelTestEnvironment.from(emptyMap())))
  }

  @Test
  fun `a relative path resolving nowhere falls back to itself, normalized`() {
    assertEquals(
      Path("does/not/exist.txt"),
      resolveRunnerPath("does/../does/not/exist.txt", BazelTestEnvironment.from(emptyMap())),
    )
  }

  @Test
  fun `a non-existing relative path resolves through the runfiles directory`() {
    val runfilesDir = Files.createTempDirectory("runfiles")
    val environment = BazelTestEnvironment.from(mapOf("RUNFILES_DIR" to runfilesDir.toString()))

    assertEquals(runfilesDir.resolve("repo/file.txt"), resolveRunnerPath("repo/file.txt", environment))
  }
}
