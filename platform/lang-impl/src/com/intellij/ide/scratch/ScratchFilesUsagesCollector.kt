package com.intellij.ide.scratch

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import java.io.File

internal class ScratchFilesUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("ide.scratch", 1)

  private val COUNT = EventFields.RoundedInt("number_of_files")
  private val TOTAL_SIZE = EventFields.RoundedLong("total_size_in_bytes")
  private val AVERAGE_SIZE = EventFields.RoundedLong("average_size_in_bytes")
  private val MAXIMUM_SIZE = EventFields.RoundedLong("maximum_size_in_bytes")
  private val SCRATCH_FILE_STATE = GROUP.registerVarargEvent("files.state", COUNT, TOTAL_SIZE, AVERAGE_SIZE, MAXIMUM_SIZE)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val (count, totalSize, maximumSize) = getScratchFileMetrics()
    return setOf(
      SCRATCH_FILE_STATE.metric(
        COUNT with count,
        TOTAL_SIZE with totalSize,
        AVERAGE_SIZE with if (count != 0) totalSize / count else 0,
        MAXIMUM_SIZE with maximumSize,
      )
    )
  }

  /**
   * @return Triple of number of files, total size of files in bytes, maximum file size
   */
  private fun getScratchFileMetrics(): Triple<Int, Long, Long> {
    val scratchRootType = try {
      RootType.findById("scratches")
    } catch (e: Exception) {
      return Triple(0, 0, 0)
    }
    val scratchPath = ScratchFileService.getInstance().getRootPath(scratchRootType)
    val scratchDirectory = File(scratchPath)

    val files = scratchDirectory.listFiles() ?: emptyArray()
    val totalSize = files.sumOf { it.length() }
    val maximumSize = files.maxOfOrNull { it.length() } ?: 0
    val fileCount = files.size

    return Triple(fileCount, totalSize, maximumSize)
  }
}