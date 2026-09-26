// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.e2e

import com.google.devtools.build.runfiles.Runfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * Runs the wasmjs_test fixtures end to end — real runner, real browser — and asserts their exit
 * codes and JUnit XML: the CI guarantee that a failing, hanging, or unloadable test module can
 * never pass as green, and that a green one reports everything it ran.
 *
 * Each fixture is executed through the runner's `--flagfile=` entry point with a controlled
 * environment; `bazel test` performs the equivalent invocation, deriving the same flagfile
 * from TEST_BINARY instead of an argument.
 */
class E2eOutcomesTest {
  /**
   * The only test that can catch an end-of-run decision taken too early: the harness ends a run
   * whose console went quiet for the completion grace period with nothing open, and that period elapsing in
   * the gap between two root suites would drop the remaining suites from the report while the run
   * still exits 0. The green fixture mixes synchronous suites with promise-returning tests (a
   * `fetch` and a dynamic `import()`), so its later suites only appear if the decision waited.
   */
  @Test
  fun `the green e2e reports every test`() {
    val run = runFixture("E2E_GREEN")

    assertEquals(run.log, 0, run.exitCode.toLong())
    listOf(
      "passes",
      "ignored",
      "configurationScriptRan",
      "readsPackageRelativeTestData",
      "moduleAdjacentImportIsRemappedToRuntimeFiles",
      "awaitedImportCompletedBeforeTheEntrypoint",
      "importsNpmPackageThroughTheImportMap",
      "compilerGeneratedJsJodaImportResolvesThroughPropagatedNpmPackages",
      "reportsHowLongItRan",
    ).forEach { name -> assertTrue("$name is missing from the report\n${run.log}", run.xml.contains(name)) }
    // A passing test's println is attributed to the test and lands in its <system-out>.
    assertTrue(run.log, run.xml.contains("<system-out>"))
    assertTrue(run.log, run.xml.contains("printed by passes"))
  }

  @Test
  fun `the green e2e reports how long a test took`() {
    val run = runFixture("E2E_GREEN")

    assertEquals(run.log, 0, run.exitCode.toLong())
    val reported = reportedTime(run.xml, "reportsHowLongItRan")
    assertNotNull("no testcase named reportsHowLongItRan in the report\n${run.log}", reported)
    // The test awaits 50ms, so anything at or above 40ms is the measurement and not a rounding
    // artefact; an exact bound would only make this flaky on a loaded machine.
    assertTrue("reportsHowLongItRan reported time=\"$reported\"\n${run.log}", reported!!.toDouble() >= 0.04)
  }

  @Test
  fun `a positive test filter selects only the matching tests`() {
    // The only check that the --test_filter chain (query parameters -> the page's process.argv
    // shim -> kotlin-test --include) actually selects: the matching-nothing case cannot tell
    // selection from a broken filter pipeline.
    val run = runFixture("E2E_GREEN", environment = mapOf("TESTBRIDGE_TEST_ONLY" to "e2e.SampleTest.passes"))

    assertEquals(run.log, 0, run.exitCode.toLong())
    assertTrue(run.log, run.xml.contains("passes"))
    assertFalse(run.log, run.xml.contains("configurationScriptRan"))
    assertFalse(run.log, run.xml.contains("importsNpmPackageThroughTheImportMap"))
  }

  @Test
  fun `an uncaught exception outside any test fails the run`() {
    val run = runFixture("E2E_THROWING")

    assertEquals(run.log, 1, run.exitCode.toLong())
    assertTrue(run.log, run.xml.contains("uncaught-exception"))
    assertTrue(run.log, run.xml.contains("detached boom"))
  }

  @Test
  fun `requested sharding is rejected as an invocation error`() {
    val run = runFixture("E2E_FAILING", environment = mapOf("TEST_TOTAL_SHARDS" to "2"))

    assertEquals(run.log, 4, run.exitCode.toLong())
    assertTrue(run.log, run.log.contains("does not support"))
  }

  @Test
  fun `a run without XML_OUTPUT_FILE still reports through the exit code`() {
    val run = runFixture("E2E_FAILING", xmlFile = { null })

    assertEquals(run.log, 1, run.exitCode.toLong())
  }

  @Test
  fun `a failing test fails the run with the failure in the xml`() {
    val run = runFixture("E2E_FAILING")

    assertEquals(run.log, 1, run.exitCode.toLong())
    assertTrue(run.log, run.xml.contains("deliberateFailure"))
    assertTrue(run.log, run.xml.contains("<failure"))
  }

  @Test
  fun `a filter matching nothing is reported as no tests executed`() {
    val run = runFixture("E2E_FAILING", environment = mapOf("TESTBRIDGE_TEST_ONLY" to "e2e.NoSuchTest"))

    assertEquals(run.log, 2, run.exitCode.toLong())
    assertTrue(run.log, run.xml.contains("no-tests-executed"))
  }

  @Test
  fun `a hanging test errors at the deadline with the reports still written`() {
    val run = runFixture("E2E_HANGING", environment = mapOf("TEST_TIMEOUT" to "40"))

    assertEquals(run.log, 1, run.exitCode.toLong())
    assertTrue(run.log, run.xml.contains("hangsForever"))
    assertTrue(run.log, run.xml.contains("<error"))
  }

  @Test
  fun `a missing awaited export is an infrastructure failure naming the export`() {
    val run = runFixture("E2E_AWAITED_MISSING")

    assertEquals(run.log, 3, run.exitCode.toLong())
    assertTrue(run.log, run.xml.contains("infrastructure-failure"))
    assertTrue(run.log, run.xml.contains("nonexistent"))
  }

  @Test
  fun `an awaited import that never resolves errors at the deadline`() {
    val run = runFixture("E2E_AWAITED_HANGING")

    assertEquals(run.log, 3, run.exitCode.toLong())
    assertTrue(run.log, run.xml.contains("infrastructure-failure"))
    assertTrue(run.log, run.xml.contains("deadline derived from TEST_TIMEOUT"))
  }

  @Test
  fun `a report that cannot be written is an infrastructure failure`() {
    // The XML path's parent is the fixture's own output *file*, so creating the report directory
    // fails: the run must end as an infrastructure failure, not an invocation error or a crash.
    val run = runFixture("E2E_FAILING", xmlFile = { workDir -> workDir.resolve("output.log/junit.xml") })

    assertEquals(run.log, 3, run.exitCode.toLong())
    assertTrue(run.log, run.log.contains("cannot write the test report"))
  }

  /** The `time` attribute of the named testcase, or null when the report has no such case. */
  private fun reportedTime(xml: String, testName: String): String? =
    Regex("<testcase[^>]*\\bname=\"" + Regex.escape(testName) + "\"[^>]*\\btime=\"([^\"]*)\"")
      .find(xml)?.groupValues?.get(1)

  private class FixtureRun(val exitCode: Int, val xml: String, val log: String)

  private fun runFixture(
    runnerVariable: String,
    environment: Map<String, String> = emptyMap(),
    // Returns where the fixture's JUnit XML report goes, or null to run without XML_OUTPUT_FILE.
    xmlFile: (workDir: Path) -> Path? = { workDir -> workDir.resolve("junit.xml") },
  ): FixtureRun {
    val runnerRlocationPath = requireNotNull(System.getenv(runnerVariable)) { "$runnerVariable is not set" }
    val runner = rlocation(runnerRlocationPath)
    val flagfile = rlocation("${runnerRlocationPath.removeSuffix(".exe")}.flagfile")
    val workDir = Files.createTempDirectory(Path(System.getenv("TEST_TMPDIR")), "fixture")
    val reportFile = xmlFile(workDir)
    val outputFile = workDir.resolve("output.log")

    val process = ProcessBuilder(runner.toString(), "--flagfile=$flagfile")
      .redirectErrorStream(true)
      // A file, not a pipe: a hanging fixture must be killable by the waitFor timeout below
      // without this test deadlocking on a full pipe buffer.
      .redirectOutput(outputFile.toFile())
      .also { builder ->
        val env = builder.environment()
        // The fixture must not inherit this wrapper's own Bazel test contract (its report
        // paths, filters, timeout, sharding); the runfiles discovery variables stay inherited.
        listOf(
          "XML_OUTPUT_FILE", "TEST_UNDECLARED_OUTPUTS_DIR", "TESTBRIDGE_TEST_ONLY",
          "TEST_TIMEOUT", "TEST_TOTAL_SHARDS", "TEST_SHARD_INDEX", "TEST_SHARD_STATUS_FILE",
          "TEST_RANDOM_SEED", "TEST_BINARY",
        ).forEach { variable -> env.remove(variable) }
        reportFile?.let { file -> env["XML_OUTPUT_FILE"] = file.toString() }
        env["TEST_TMPDIR"] = workDir.toString()
        // The harness bounds page load and the completion loop by the deadline it derives from
        // TEST_TIMEOUT, so a fixture without one would never give up. Set before the per-test
        // environment so a test asking for a different budget still wins.
        env["TEST_TIMEOUT"] = FIXTURE_TEST_TIMEOUT_SECONDS
        env.putAll(environment)
      }
      .start()
    val finished = process.waitFor(180, TimeUnit.SECONDS)
    when {
      finished -> Unit
      else -> {
        process.destroyForcibly().waitFor()
        error("the fixture did not finish within 180s:\n${outputFile.readText()}")
      }
    }

    val xml = when {
      reportFile != null && Files.exists(reportFile) -> reportFile.readText()
      else -> ""
    }
    return FixtureRun(
      exitCode = process.exitValue(),
      xml = xml,
      log = "--- fixture output ---\n${outputFile.readText()}\n--- junit xml ---\n${xml.ifEmpty { "<missing>" }}",
    )
  }

  private fun rlocation(path: String): Path = Path(runfiles.rlocation(path))

  private val runfiles = Runfiles.preload().withSourceRepository("")
}

/** softDeadline(60s) = 45s: well inside the 180s watchdog, and far above the few seconds a green run takes. */
private const val FIXTURE_TEST_TIMEOUT_SECONDS = "60"
