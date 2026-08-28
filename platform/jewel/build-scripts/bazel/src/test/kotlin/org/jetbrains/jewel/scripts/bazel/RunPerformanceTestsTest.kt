package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

class RunPerformanceTestsTest {
    @Test
    fun `wildcardToClassNameRegex converts a single wildcard into a permissive regex`() {
        assertEquals(".*\\QPerformance\\E.*", wildcardToClassNameRegex("*Performance*"))
    }

    @Test
    fun `wildcardToClassNameRegex escapes regex metacharacters in the literal parts`() {
        assertEquals("\\Qorg.jetbrains.jewel.\\E.*", wildcardToClassNameRegex("org.jetbrains.jewel.*"))
    }

    @Test
    fun `wildcardToClassNameRegex leaves a pattern with no wildcard untouched but escaped`() {
        assertEquals("\\QTablePerformanceTest\\E", wildcardToClassNameRegex("TablePerformanceTest"))
    }

    @Test
    fun `buildClassNameFilters converts a single pattern into one include-classname filter`() {
        assertEquals("include-classname=.*\\QPerformance\\E.*", buildClassNameFilters("*Performance*"))
    }

    @Test
    fun `buildClassNameFilters joins multiple comma-separated patterns with semicolons`() {
        assertEquals(
            "include-classname=.*\\QPerformance\\E.*;include-classname=.*\\QBenchmark\\E.*",
            buildClassNameFilters("*Performance*,*Benchmark*"),
        )
    }

    @Test
    fun `buildClassNameFilters trims whitespace around each pattern`() {
        assertEquals(
            "include-classname=.*\\QPerformance\\E.*;include-classname=.*\\QBenchmark\\E.*",
            buildClassNameFilters(" *Performance* , *Benchmark* "),
        )
    }

    @Test
    fun `buildClassNameFilters drops empty patterns`() {
        assertEquals("include-classname=.*\\QPerformance\\E.*", buildClassNameFilters("*Performance*,,"))
    }

    @Test
    fun `buildHeapJvmOpts sets both min and max heap to the same size`() {
        assertEquals(listOf("-Xmx4g", "-Xms4g"), buildHeapJvmOpts("4g"))
    }

    @Test
    fun `buildJfrJvmOpts includes the heap opts plus JFR recording flags`() {
        val jvmOpts = buildJfrJvmOpts("4g", File("/tmp/run_1.jfr"), 60)

        assertEquals(
            listOf(
                "-Xmx4g",
                "-Xms4g",
                "-XX:StartFlightRecording=filename=/tmp/run_1.jfr,duration=60s,settings=profile",
                "-XX:FlightRecorderOptions=stackdepth=256",
            ),
            jvmOpts,
        )
    }

    @Test
    fun `buildBazelTestCommand assembles the target, filters, jvmopts, and cache-busting flags`() {
        val command =
            buildBazelTestCommand(
                target = "//platform/jewel/ui-tests:ui-tests_test",
                classNameFilters = "include-classname=.*Performance.*",
                jvmOpts = listOf("-Xmx4g", "-Xms4g"),
            )

        assertEquals(
            "./bazel.cmd test //platform/jewel/ui-tests:ui-tests_test " +
                "--test_env=JB_TEST_JUNIT5_FILTERS=include-classname=.*Performance.* " +
                "--jvmopt=-Xmx4g --jvmopt=-Xms4g --nocache_test_results --test_output=streamed",
            command,
        )
    }

    @Test
    fun `buildBazelTestCommand omits jvmopt flags when there are none`() {
        val command =
            buildBazelTestCommand(
                target = "//platform/jewel/ui-tests:ui-tests_test",
                classNameFilters = "include-classname=.*Performance.*",
                jvmOpts = emptyList(),
            )

        assertEquals(
            "./bazel.cmd test //platform/jewel/ui-tests:ui-tests_test " +
                "--test_env=JB_TEST_JUNIT5_FILTERS=include-classname=.*Performance.* " +
                "--nocache_test_results --test_output=streamed",
            command,
        )
    }
}
