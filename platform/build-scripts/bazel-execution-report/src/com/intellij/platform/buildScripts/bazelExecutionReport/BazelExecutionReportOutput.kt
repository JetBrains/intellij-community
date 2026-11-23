// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReport.Blob
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReport.Source
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReport.Summary
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReport.TEST_MNEMONIC
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes.BUILD_PROBLEM
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes.BUILD_STATISTIC_VALUE
import java.io.BufferedWriter
import kotlin.math.roundToLong

internal object BazelExecutionReportOutput {
  const val BYTES_IN_MB = 1024L * 1024L
  private const val BYTES_IN_GB = 1024L * 1024L * 1024L

  fun BufferedWriter.reportSummary(summary: Summary) {
    reportSources("actions", summary.actions, summary)
    reportSources("test actions ($TEST_MNEMONIC)", summary.testActions, summary)
    write("note: the counts leave out the hits of the local action cache, because the execution log does not hold them\n")

    val local = summary.localActionsByMnemonic
    write("actions run locally by action kind: ${local.size} action kinds, ${formatWallTime(summary.localWallTimeNanos)} wall time, ")
    write("${formatWallTime(summary.localQueueTimeNanos)} queue time\n")
    val countWidth = local.maxOfOrNull { it.count.toString().length } ?: 0
    val timeWidth = local.maxOfOrNull { formatWallTime(it.wallTimeNanos).length } ?: 0
    val queueWidth = local.maxOfOrNull { formatWallTime(it.queueTimeNanos).length } ?: 0
    for (actions in local) {
      write("  ${actions.count.toString().padStart(countWidth)}  ${formatWallTime(actions.wallTimeNanos).padStart(timeWidth)}")
      write("  ${formatWallTime(actions.queueTimeNanos).padStart(queueWidth)}  ${actions.mnemonic}\n")
    }
    write("note: the wall time is the sum over the actions, not CPU time. Parallel actions can make it exceed the build time\n")
    write("note: the queue time is the wait for the local resources and, for a worker, for a free worker slot\n")

    write("uploadable outputs of the actions run locally: ${blobCount(summary.uploads)}, ${formatSize(summary.uploads.values.sum())}\n")
    for ((mnemonic, blobs) in summary.uploadsByMnemonic) {
      write("  ${formatSize(blobs.values.sum()).padStart(12)}  ${blobCount(blobs)}  $mnemonic\n")
    }

    write("test action list:\n")
    val sourceWidth = Source.entries.maxOf { it.text.length }
    val uploads = summary.tests.map { summary.uploadText(it) }
    val uploadWidth = uploads.maxOfOrNull { it.length } ?: 0
    for ((test, upload) in summary.tests.zip(uploads)) {
      val exit = if (test.exitCode == 0) "" else "  exit ${test.exitCode}"
      write("  ${test.source().text.padEnd(sourceWidth)}  ${upload.padStart(uploadWidth)}  ${test.targetLabel.ifEmpty { "<no target>" }}$exit\n")
    }

    write("largest blobs: ${blobTotalText(summary.blobCount, summary.blobSizeBytes, summary.largestBlobs.size)}\n")
    for (blob in summary.largestBlobs) {
      reportRow(blob, indent = "  ")
    }
  }

  /**
   * Writes one count for each source that [Summary.shownSources] returns.
   */
  private fun BufferedWriter.reportSources(title: String, counts: Map<Source, Int>, summary: Summary) {
    write("$title: ${counts.values.sum()}\n")
    for (source in summary.shownSources()) {
      write("  ${source.text}: ${counts[source] ?: 0}\n")
    }
  }

  /**
   * Writes the blobs that break the blob size limit.
   */
  fun BufferedWriter.reportViolations(violations: List<Blob>, options: BazelExecutionReportOptions) {
    if (violations.isEmpty()) {
      write("size check: no blob breaks the ${limitText(options)}\n")
      return
    }
    val verb = if (violations.size == 1) "blob breaks" else "blobs break"
    write("size check: ${violations.size} $verb the ${limitText(options)}\n")
    for (blob in violations) {
      reportRow(blob, indent = "  ")
    }
  }

  private fun BufferedWriter.reportRow(blob: Blob, indent: String) {
    val size = formatSize(blob.sizeBytes).padStart(12)
    val targets = blob.targets.joinToString(" ")
    val mnemonics = blob.mnemonics.joinToString(",")
    write("$indent$size  x${blob.paths.size}  $targets  [$mnemonics]  ${blob.paths.first()}\n")
  }

  /**
   * Writes the queue time of each action kind run locally as a TeamCity build statistic value, in milliseconds.
   *
   * A chart of the value shows when the actions of a worker, such as `IjPluginPackaging`, start to wait for a slot or for the resources.
   */
  fun BufferedWriter.reportStatistics(summary: Summary) {
    for (actions in summary.localActionsByMnemonic) {
      val key = "bazel.queueTimeMs.${actions.mnemonic}"
      write(ServiceMessage.asString(BUILD_STATISTIC_VALUE, mapOf("key" to key, "value" to (actions.queueTimeNanos / 1_000_000L).toString())))
      write("\n")
    }
  }

  /**
   * Writes one TeamCity build problem for each blob that breaks the blob size limit.
   *
   * The identity comes from the first path of the blob, so TeamCity tracks the same output across builds.
   * TeamCity limits an identity to 60 characters, so the identity holds a hash of the path, not the path.
   */
  fun BufferedWriter.reportBuildProblems(violations: List<Blob>, options: BazelExecutionReportOptions) {
    for (blob in violations) {
      val path = blob.paths.first()
      val description = "${formatSize(blob.sizeBytes)} blob breaks the ${limitText(options)}: $path (${blob.targets.joinToString(" ")})"
      val identity = "bazel-blob-size-${Integer.toHexString(path.hashCode())}"
      write(ServiceMessage.asString(BUILD_PROBLEM, mapOf("description" to description, "identity" to identity)))
      write("\n")
    }
  }

  /**
   * Names the blob size limit, for the report and for the exception.
   */
  fun limitText(options: BazelExecutionReportOptions): String {
    val size = "${formatSize(options.maxSizeBytes)} limit"
    return if (options.extensions.isEmpty()) size else "$size for ${options.extensions.sorted().joinToString(", ")}"
  }

  fun blobCount(blobs: Map<String, Long>): String = "${blobs.size} ${if (blobs.size == 1) "blob" else "blobs"}"

  fun blobTotalText(count: Int, sizeBytes: Long, shown: Int): String {
    val noun = if (count == 1) "blob" else "blobs"
    val tail = if (shown < count) " ($shown shown)" else ""
    return "$count $noun, ${formatSize(sizeBytes)} total$tail"
  }

  /**
   * Formats a duration as seconds to one decimal place, for example `225.3 s`.
   */
  fun formatWallTime(nanos: Long): String {
    val tenths = (nanos + 50_000_000L) / 100_000_000L
    return "${tenths / 10}.${tenths % 10} s"
  }

  fun formatSize(sizeBytes: Long): String {
    return if (sizeBytes >= BYTES_IN_GB) "${formatHundredths(sizeBytes / BYTES_IN_GB.toDouble())} gb" else "${formatHundredths(sizeBytes / BYTES_IN_MB.toDouble())} mb"
  }

  /**
   * Formats a number to two decimal places, and drops a trailing zero.
   */
  private fun formatHundredths(value: Double): String {
    val hundredths = (value * 100).roundToLong()
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toInt()
    return when {
      fraction == 0 -> whole.toString()
      fraction % 10 == 0 -> "$whole.${fraction / 10}"
      else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
  }
}