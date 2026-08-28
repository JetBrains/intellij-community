package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ComparePerformanceTest {
    private val command = ComparePerformanceCommand()

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
        val file = createSafeTempDir("compare-performance-test").resolve("recording.jfr")
        try {
            file.writeText("")
            assertEquals(listOf(file), command.getJfrFiles(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `getJfrFiles returns an empty list for a non-jfr file`() {
        val file = createSafeTempDir("compare-performance-test").resolve("not-a-recording.txt")
        try {
            file.writeText("")
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
    fun `extractDuration returns whatever the reader reports`() = runTest {
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(durationMillis = { 2_500L }))

        assertEquals(2_500L, commandUnderTest.extractDuration(File("recording.jfr")))
    }

    @Test
    fun `extractTotalSamples returns whatever the reader reports`() = runTest {
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(executionSampleCount = { 3 }))

        assertEquals(3, commandUnderTest.extractTotalSamples(File("recording.jfr")))
    }

    @Test
    fun `extractAllocations returns whatever the reader reports`() = runTest {
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(allocationSampleCount = { 2 }))

        assertEquals(2, commandUnderTest.extractAllocations(File("recording.jfr")))
    }

    @Test
    fun `extractTopMethods returns whatever the reader reports`() = runTest {
        val methodCounts = mapOf("org.jetbrains.jewel.ui.Real.method" to 1)
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(topMethodCounts = { methodCounts }))

        assertEquals(methodCounts, commandUnderTest.extractTopMethods(File("recording.jfr")))
    }

    @Test
    fun `extractGarbageCollectionStats aggregates GC pause durations`() = runTest {
        val commandUnderTest =
            ComparePerformanceCommand(FakeJfrMetricsReader(gcPauseDurationsMs = { listOf(10L, 20L) }))

        val result = commandUnderTest.extractGarbageCollectionStats(File("recording.jfr"))

        assertEquals(2, result.totalGcCount)
        assertEquals(30L, result.totalGcPauseTimeMs)
        assertEquals(20L, result.maxGcPauseTimeMs)
    }

    @Test
    fun `extractGarbageCollectionStats returns defaults when there are no GC events`() = runTest {
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(gcPauseDurationsMs = { emptyList() }))

        val result = commandUnderTest.extractGarbageCollectionStats(File("recording.jfr"))

        assertEquals(ComparePerformanceCommand.GarbageCollectionStats(), result)
    }

    @Test
    fun `extractCpuLoadStats averages JVM and machine CPU load`() = runTest {
        val samples =
            listOf(
                JfrEventReader.CpuLoadSample(0.10, 0.05, 0.30),
                JfrEventReader.CpuLoadSample(0.20, 0.05, 0.50),
            )
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(cpuLoadSamples = { samples }))

        val result = commandUnderTest.extractCpuLoadStats(File("recording.jfr"))

        assertEquals(20.0, result.avgJvmCpu) // avg(0.15, 0.25) * 100
        assertEquals(40.0, result.avgMachineCpu) // avg(0.30, 0.50) * 100
        assertEquals(25.0, result.maxJvmCpu)
        assertEquals(50.0, result.maxMachineCpu)
    }

    @Test
    fun `extractHeapSummaryStats converts heap bytes to megabytes`() = runTest {
        val samples = listOf(JfrEventReader.HeapSummarySample(10485760L, 20971520L))
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(heapSummarySamples = { samples }))

        val result = commandUnderTest.extractHeapSummaryStats(File("recording.jfr"))

        assertEquals(10L, result.maxHeapUsedMb)
        assertEquals(10.0, result.avgHeapUsedMb)
        assertEquals(20.0, result.avgCommittedSizeMb)
    }

    @Test
    fun `extractMetaspaceSummaryStats converts nested metaspace bytes to megabytes`() = runTest {
        val samples = listOf(JfrEventReader.MetaspaceSummarySample(5242880L, 10485760L))
        val commandUnderTest = ComparePerformanceCommand(FakeJfrMetricsReader(metaspaceSummarySamples = { samples }))

        val result = commandUnderTest.extractMetaspaceSummaryStats(File("recording.jfr"))

        assertEquals(5L, result.maxMetaspaceUsedMb)
        assertEquals(10.0, result.avgMetaspaceCommittedMb)
    }
}
