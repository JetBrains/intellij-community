package org.jetbrains.jewel.scripts.bazel

import java.io.File

internal class FakeJfrMetricsReader(
    private val durationMillis: (File) -> Long = { 0L },
    private val executionSampleCount: (File) -> Int = { 0 },
    private val allocationSampleCount: (File) -> Int = { 0 },
    private val topMethodCounts: (File) -> Map<String, Int> = { emptyMap() },
    private val gcPauseDurationsMs: (File) -> List<Long> = { emptyList() },
    private val cpuLoadSamples: (File) -> List<JfrEventReader.CpuLoadSample> = { emptyList() },
    private val heapSummarySamples: (File) -> List<JfrEventReader.HeapSummarySample> = { emptyList() },
    private val metaspaceSummarySamples: (File) -> List<JfrEventReader.MetaspaceSummarySample> = { emptyList() },
) : JfrMetricsReader {
    override fun readDurationMillis(file: File) = durationMillis(file)

    override fun readExecutionSampleCount(file: File) = executionSampleCount(file)

    override fun readAllocationSampleCount(file: File) = allocationSampleCount(file)

    override fun readTopMethodCounts(file: File) = topMethodCounts(file)

    override fun readGcPauseDurationsMs(file: File) = gcPauseDurationsMs(file)

    override fun readCpuLoadSamples(file: File) = cpuLoadSamples(file)

    override fun readHeapSummarySamples(file: File) = heapSummarySamples(file)

    override fun readMetaspaceSummarySamples(file: File) = metaspaceSummarySamples(file)
}
