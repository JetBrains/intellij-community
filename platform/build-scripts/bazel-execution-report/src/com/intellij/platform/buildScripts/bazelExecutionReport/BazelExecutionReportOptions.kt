// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.BYTES_IN_MB
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal class BazelExecutionReportOptions(
  /** The logs to merge. A directory gives its logs in the order of their names. */
  @JvmField val files: List<Path>,
  /** The row limit of the list of largest blobs. 0 shows every row. */
  @JvmField val largestBlobsCount: Int,
  /** The blob size limit. A blob above it breaks the limit. */
  @JvmField val maxSizeBytes: Long,
  /** The file extensions the size check covers. An empty set covers every extension. */
  @JvmField val extensions: Set<String>,
  /** Reports whether the command line asked for the size check. A negative `--blob-size-limit-mb` turns it off. */
  @JvmField val checkBlobSizeViolations: Boolean,
  /** The HTML page to write the report to, or null. */
  @JvmField val html: Path?,
) {
  companion object {
    private const val DEFAULT_LARGEST_BLOBS_COUNT = 20

    fun parse(args: Array<String>): BazelExecutionReportOptions {
      var input: Path? = null
      var largestBlobsCount = DEFAULT_LARGEST_BLOBS_COUNT
      var maxSizeBytes = 0L
      var extensions = emptySet<String>()
      var checkBlobSizeViolations = false
      var html: Path? = null

      var i = 0
      while (i < args.size) {
        val arg = args[i]
        when (arg) {
          "--largest-blobs-count" -> {
            val value = args.getOrNull(++i) ?: error("--largest-blobs-count needs a row count")
            largestBlobsCount = value.toIntOrNull() ?: error("--largest-blobs-count needs a row count, and got $value")
          }
          "--blob-size-limit-mb" -> {
            val value = args.getOrNull(++i) ?: error("--blob-size-limit-mb needs a size in mb")
            val mb = value.toLongOrNull() ?: error("--blob-size-limit-mb needs a size in mb, and got $value")
            if (mb > 0) {
              maxSizeBytes = mb * BYTES_IN_MB
              checkBlobSizeViolations = true
            }
          }
          "--html-report-path" -> html = Path(args.getOrNull(++i) ?: error("--html-report-path needs a file path"))
          "--blob-size-limit-extensions" -> {
            val value = args.getOrNull(++i) ?: error("--blob-size-limit-extensions needs a list of file extensions")
            extensions = value.split(',').map { it.trim().removePrefix(".").lowercase() }.filterTo(HashSet()) { it.isNotEmpty() }
            require(extensions.isNotEmpty()) { "--blob-size-limit-extensions needs a list of file extensions, and got $value" }
            checkBlobSizeViolations = true
          }
          else -> {
            if (arg.startsWith("-")) {
              error("unknown option $arg")
            }
            require(input == null) { "give one log file or one directory, and got $input and $arg" }
            input = Path(arg)
          }
        }
        i++
      }
      requireNotNull(input) {
        "No log file or directory given"
      }
      val files = logFiles(input)
      for (file in files) {
        require(file.isReadable()) {
          "Cannot read $file"
        }
        require(BazelCompactExecutionLog.isCompact(file)) {
          "$file is not a compact execution log. Write the log with --execution_log_compact_file"
        }
      }
      require(!checkBlobSizeViolations || maxSizeBytes > 0) {
        "--blob-size-limit-extensions needs a positive --blob-size-limit-mb"
      }
      return BazelExecutionReportOptions(
        files = files,
        largestBlobsCount = largestBlobsCount,
        maxSizeBytes = maxSizeBytes,
        extensions = extensions,
        checkBlobSizeViolations = checkBlobSizeViolations,
        html = html,
      )
    }

    /**
     * Returns the logs to read: the file itself, or the `*.zst` files directly in the directory, in the order of their names.
     */
    private fun logFiles(input: Path): List<Path> {
      if (!input.isDirectory()) {
        return listOf(input)
      }
      val files = input.listDirectoryEntries("*.zst")
        .asSequence()
        .filter { it.isRegularFile() }
        .sortedBy { it.name }
        .toList()
      require(files.any()) {
        "$input holds no *.zst log file"
      }
      return files
    }
  }
}