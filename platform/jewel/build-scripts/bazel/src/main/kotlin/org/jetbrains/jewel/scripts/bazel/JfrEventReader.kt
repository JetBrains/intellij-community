package org.jetbrains.jewel.scripts.bazel

import java.nio.file.Path
import java.time.Duration
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile

/**
 * Reads .jfr files in-process via jdk.jfr.consumer.RecordingFile, instead of shelling out to the `jfr` CLI
 * tool. Requires jvm_target 21+ (see the compare-performance target's kotlinc_opts) since jdk.jfr.consumer's
 * APIs don't resolve under this Bazel toolchain's default jvm_target of 1.8.
 */
internal object JfrEventReader {
    fun readDurationMillis(file: Path): Long {
        val sampleTimes = readEvents(file, "jdk.ExecutionSample").map { it.startTime }.sorted()
        if (sampleTimes.size < 2) return 0L
        return Duration.between(sampleTimes.first(), sampleTimes.last()).toMillis()
    }

    fun readExecutionSampleCount(file: Path): Int = readEvents(file, "jdk.ExecutionSample").size

    fun readAllocationSampleCount(file: Path): Int = readEvents(file, "jdk.ObjectAllocationSample").size

    fun readTopMethodCounts(file: Path): Map<String, Int> {
        val methodCounts = mutableMapOf<String, Int>()
        for (event in readEvents(file, "jdk.ExecutionSample")) {
            for (frame in event.stackTrace?.frames.orEmpty()) {
                val method = frame.method ?: continue
                val fullMethod = "${method.type?.name ?: "?"}.${method.name}"
                if (
                    fullMethod.startsWith("java.") ||
                        fullMethod.startsWith("jdk.") ||
                        fullMethod.startsWith("sun.") ||
                        fullMethod.startsWith("kotlin.coroutines.") ||
                        fullMethod.startsWith("kotlinx.coroutines.")
                ) {
                    continue
                }
                methodCounts.merge(fullMethod, 1, Int::plus)
            }
        }
        return methodCounts
    }

    fun readGcPauseDurationsMs(file: Path): List<Long> = readEvents(file, "jdk.GarbageCollection").map { it.duration.toMillis() }

    fun readCpuLoadSamples(file: Path): List<CpuLoadSample> =
        readEvents(file, "jdk.CPULoad").map {
            CpuLoadSample(
                jvmUser = it.getFloat("jvmUser").toDouble(),
                jvmSystem = it.getFloat("jvmSystem").toDouble(),
                machineTotal = it.getFloat("machineTotal").toDouble(),
            )
        }

    // jdk.GCHeapSummary's own fields are [startTime, gcId, when, heapSpace, heapUsed] -- committedSize lives
    // nested under the "heapSpace" composite field, not at the top level. Verified against a real recording.
    fun readHeapSummarySamples(file: Path): List<HeapSummarySample> =
        readEvents(file, "jdk.GCHeapSummary").map { event ->
            val heapSpace = if (event.hasField("heapSpace")) event.getValue<Any?>("heapSpace") else null
            val committedSize =
                (heapSpace as? jdk.jfr.consumer.RecordedObject)?.takeIf { it.hasField("committedSize") }?.getLong("committedSize")
                    ?: 0L
            HeapSummarySample(heapUsed = event.getLong("heapUsed"), committedSize = committedSize)
        }

    fun readMetaspaceSummarySamples(file: Path): List<MetaspaceSummarySample> =
        readEvents(file, "jdk.MetaspaceSummary").mapNotNull { event ->
            if (!event.hasField("metaspace")) return@mapNotNull null
            val metaspace = event.getValue<jdk.jfr.consumer.RecordedObject?>("metaspace") ?: return@mapNotNull null
            MetaspaceSummarySample(used = metaspace.getLong("used"), committed = metaspace.getLong("committed"))
        }

    private fun readEvents(file: Path, eventTypeName: String): List<RecordedEvent> =
        RecordingFile.readAllEvents(file).filter { it.eventType.name == eventTypeName }

    data class CpuLoadSample(val jvmUser: Double, val jvmSystem: Double, val machineTotal: Double)

    data class HeapSummarySample(val heapUsed: Long, val committedSize: Long)

    data class MetaspaceSummarySample(val used: Long, val committed: Long)
}
