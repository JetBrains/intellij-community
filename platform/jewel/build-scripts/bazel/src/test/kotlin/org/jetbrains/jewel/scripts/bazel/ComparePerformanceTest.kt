package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ComparePerformanceTest {
    private val fakeRunner = FakeCommandRunner { CmdResult.Success("") }
    private val command = ComparePerformanceCommand(fakeRunner)

    @Test
    fun `extractBaseTestName strips an underscore run suffix`() {
        assertEquals("test", command.extractBaseTestName("test_run1.jfr"))
    }

    @Test
    fun `extractBaseTestName strips an underscore run suffix with its own separator`() {
        assertEquals("test", command.extractBaseTestName("test_run_1.jfr"))
    }

    @Test
    fun `extractBaseTestName strips a dot run suffix`() {
        assertEquals("test", command.extractBaseTestName("test.run2.jfr"))
    }

    @Test
    fun `extractBaseTestName strips a dash run suffix`() {
        assertEquals("test", command.extractBaseTestName("test-run3.jfr"))
    }

    @Test
    fun `extractBaseTestName strips a bare numeric suffix`() {
        assertEquals("test", command.extractBaseTestName("test_001.jfr"))
    }

    @Test
    fun `extractBaseTestName leaves a name with no run suffix untouched`() {
        assertEquals("test", command.extractBaseTestName("test.jfr"))
    }

    @Test
    fun `getJfrFiles returns a single jfr file as-is`() {
        val file = File.createTempFile("recording", ".jfr")
        try {
            assertEquals(listOf(file), command.getJfrFiles(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `getJfrFiles returns an empty list for a non-jfr file`() {
        val file = File.createTempFile("not-a-recording", ".txt")
        try {
            assertTrue(command.getJfrFiles(file).isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `getJfrFiles finds jfr files in a directory sorted by name`() {
        val dir = createSafeTempDir("compare-performance-test").resolve("jfr-dir").also { it.mkdirs() }
        try {
            File(dir, "b.jfr").writeText("")
            File(dir, "a.jfr").writeText("")
            File(dir, "ignored.txt").writeText("")

            val result = command.getJfrFiles(dir).map { it.name }

            assertEquals(listOf("a.jfr", "b.jfr"), result)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `getJfrFiles returns an empty list for a path that is neither a file nor a directory`() {
        val missing = File("/nonexistent/path/for/compare-performance-test")

        assertTrue(command.getJfrFiles(missing).isEmpty())
    }

    @Test
    fun `calculateChange returns null when the baseline is zero`() {
        assertNull(command.calculateChange(0L, 100L))
    }

    @Test
    fun `calculateChange returns a positive percentage for an increase`() {
        assertEquals(50.0, command.calculateChange(100L, 150L))
    }

    @Test
    fun `calculateChange returns a negative percentage for a decrease`() {
        assertEquals(-50.0, command.calculateChange(100L, 50L))
    }

    @Test
    fun `calculateStatistics computes mean, median, mode, min and max for an odd-sized list`() {
        val stats = command.calculateStatistics(listOf(1L, 2L, 2L, 3L, 10L))

        assertEquals(3L, stats.mean) // (1 + 2 + 2 + 3 + 10) / 5 = 3.6, truncated to 3
        assertEquals(2L, stats.median)
        assertEquals(2L, stats.mode)
        assertEquals(1L, stats.min)
        assertEquals(10L, stats.max)
    }

    @Test
    fun `calculateStatistics averages the two middle values for an even-sized list`() {
        val stats = command.calculateStatistics(listOf(1L, 2L, 3L, 4L))

        assertEquals(2L, stats.median) // (2 + 3) / 2, integer division
    }

    @Test
    fun `calculateStatistics reports zero standard deviation for a single value`() {
        val stats = command.calculateStatistics(listOf(42L))

        assertEquals(0.0, stats.stdDev)
        assertEquals(42L, stats.mean)
        assertEquals(42L, stats.median)
        assertEquals(42L, stats.mode)
    }

    @Test
    fun `parseTimestamp converts hours, minutes, seconds and millis to total milliseconds`() {
        assertEquals(3_723_456L, command.parseTimestamp("01:02:03.456"))
    }

    @Test
    fun `parseTimestamp tolerates a missing millis component`() {
        assertEquals(3_723_000L, command.parseTimestamp("01:02:03"))
    }

    @Test
    fun `parseTimestamp returns zero for a malformed timestamp`() {
        assertEquals(0L, command.parseTimestamp("not-a-timestamp"))
    }

    @Test
    fun `parseDurationToMillis converts an ISO-8601 PT duration to milliseconds`() {
        assertEquals(3685L, command.parseDurationToMillis("PT3.685459S"))
    }

    @Test
    fun `parseDurationToMillis returns null for a malformed duration`() {
        assertNull(command.parseDurationToMillis("not-a-duration"))
    }

    @Test
    fun `extractDuration returns the span between the first and last startTime`() = runTest {
        val output =
            """
            jdk.ExecutionSample {
              startTime = 00:00:01.000
            }
            jdk.ExecutionSample {
              startTime = 00:00:03.500
            }
            """
                .trimIndent()
        val runner = FakeCommandRunner { CmdResult.Success(output) }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractDuration(File("recording.jfr"))

        assertEquals(2_500L, result)
    }

    @Test
    fun `extractDuration returns zero when the underlying command fails`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Failure("jfr: command not found") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        assertEquals(0L, commandUnderTest.extractDuration(File("recording.jfr")))
    }

    @Test
    fun `extractDuration returns zero when fewer than two startTime lines are found`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Success("startTime = 00:00:01.000") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        assertEquals(0L, commandUnderTest.extractDuration(File("recording.jfr")))
    }

    @Test
    fun `extractTotalSamples counts one line per sample event`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Success("sample1\nsample2\nsample3") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        assertEquals(3, commandUnderTest.extractTotalSamples(File("recording.jfr")))
    }

    @Test
    fun `extractTotalSamples returns zero when the underlying command fails`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Failure("") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        assertEquals(0, commandUnderTest.extractTotalSamples(File("recording.jfr")))
    }

    @Test
    fun `extractAllocations counts one line per allocation event`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Success("alloc1\nalloc2") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        assertEquals(2, commandUnderTest.extractAllocations(File("recording.jfr")))
    }

    @Test
    fun `extractTopMethods extracts and counts jewel method signatures from stack frames`() = runTest {
        val output =
            """
            org.jetbrains.jewel.foundation.lazy.table.ClassName.methodName(bci=0, line=42)
            org.jetbrains.jewel.foundation.lazy.table.ClassName.methodName(bci=1, line=43)
            org.jetbrains.jewel.ui.OtherClass.otherMethod(bci=0, line=1)
            """
                .trimIndent()
        val runner = FakeCommandRunner { CmdResult.Success(output) }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractTopMethods(File("recording.jfr"))

        assertEquals(
            mapOf(
                "org.jetbrains.jewel.foundation.lazy.table.ClassName.methodName" to 2,
                "org.jetbrains.jewel.ui.OtherClass.otherMethod" to 1,
            ),
            result,
        )
    }

    @Test
    fun `extractTopMethods filters out JVM and coroutine internals`() = runTest {
        val output =
            """
            java.lang.Thread.run(bci=0, line=1)
            jdk.internal.misc.Unsafe.park(bci=0, line=1)
            sun.nio.ch.Poller.poll(bci=0, line=1)
            kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(bci=0, line=1)
            kotlinx.coroutines.DispatchedTask.run(bci=0, line=1)
            org.jetbrains.jewel.ui.Real.method(bci=0, line=1)
            """
                .trimIndent()
        val runner = FakeCommandRunner { CmdResult.Success(output) }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractTopMethods(File("recording.jfr"))

        assertEquals(mapOf("org.jetbrains.jewel.ui.Real.method" to 1), result)
    }

    @Test
    fun `extractTopMethods returns an empty map when the underlying command fails`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Failure("") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        assertTrue(commandUnderTest.extractTopMethods(File("recording.jfr")).isEmpty())
    }

    @Test
    fun `extractGarbageCollectionStats aggregates GC pause durations from JFR JSON output`() = runTest {
        val json =
            """
            {"recording":{"events":[
              {"values":{"duration":"PT0.010S"}},
              {"values":{"duration":"PT0.020S"}}
            ]}}
            """
                .trimIndent()
        val runner = FakeCommandRunner { CmdResult.Success(json) }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractGarbageCollectionStats(File("recording.jfr"))

        assertEquals(2, result.totalGcCount)
        assertEquals(30L, result.totalGcPauseTimeMs)
        assertEquals(20L, result.maxGcPauseTimeMs)
    }

    @Test
    fun `extractGarbageCollectionStats returns defaults when the output is not valid JSON`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Success("not json") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractGarbageCollectionStats(File("recording.jfr"))

        assertEquals(ComparePerformanceCommand.GarbageCollectionStats(), result)
    }

    @Test
    fun `extractGarbageCollectionStats returns defaults when the output is blank`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Success("") }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractGarbageCollectionStats(File("recording.jfr"))

        assertEquals(0, result.totalGcCount)
    }

    @Test
    fun `extractCpuLoadStats averages JVM and machine CPU load from JFR JSON output`() = runTest {
        val json =
            """
            {"recording":{"events":[
              {"values":{"jvmUser":0.10,"jvmSystem":0.05,"machineTotal":0.30}},
              {"values":{"jvmUser":0.20,"jvmSystem":0.05,"machineTotal":0.50}}
            ]}}
            """
                .trimIndent()
        val runner = FakeCommandRunner { CmdResult.Success(json) }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractCpuLoadStats(File("recording.jfr"))

        assertEquals(20.0, result.avgJvmCpu) // avg(0.15, 0.25) * 100
        assertEquals(40.0, result.avgMachineCpu) // avg(0.30, 0.50) * 100
        assertEquals(25.0, result.maxJvmCpu)
        assertEquals(50.0, result.maxMachineCpu)
    }

    @Test
    fun `extractHeapSummaryStats converts heap bytes to megabytes from JFR JSON output`() = runTest {
        val json =
            """
            {"recording":{"events":[
              {"values":{"heapUsed":10485760,"committedSize":20971520}}
            ]}}
            """
                .trimIndent()
        val runner = FakeCommandRunner { CmdResult.Success(json) }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractHeapSummaryStats(File("recording.jfr"))

        assertEquals(10L, result.maxHeapUsedMb)
        assertEquals(10.0, result.avgHeapUsedMb)
        assertEquals(20.0, result.avgCommittedSizeMb)
    }

    @Test
    fun `extractMetaspaceSummaryStats converts nested metaspace bytes to megabytes from JFR JSON output`() = runTest {
        val json =
            """
            {"recording":{"events":[
              {"values":{"metaspace":{"used":5242880,"committed":10485760}}}
            ]}}
            """
                .trimIndent()
        val runner = FakeCommandRunner { CmdResult.Success(json) }
        val commandUnderTest = ComparePerformanceCommand(runner)

        val result = commandUnderTest.extractMetaspaceSummaryStats(File("recording.jfr"))

        assertEquals(5L, result.maxMetaspaceUsedMb)
        assertEquals(10.0, result.avgMetaspaceCommittedMb)
    }
}
