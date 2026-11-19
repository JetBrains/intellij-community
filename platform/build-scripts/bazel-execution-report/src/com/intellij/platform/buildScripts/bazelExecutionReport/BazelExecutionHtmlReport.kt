// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.blobCount
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.formatSize
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.formatWallTime
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReport.Source
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReport.Summary
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.blobTotalText
import java.io.BufferedWriter
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createParentDirectories

/**
 * Writes the summary of [BazelExecutionReport] as a self-contained HTML page.
 *
 * The page holds its style inline and uses no script, so a TeamCity report tab can show it in its frame.
 * The page shows the same numbers as the build log summary.
 */
internal object BazelExecutionHtmlReport {
  private const val TITLE = "Bazel execution report"

  private val STYLE = """
    body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif; font-size: 13px; color: #1f2328; margin: 16px; }
    h1 { font-size: 18px; margin: 0 0 8px; }
    h2 { font-size: 15px; margin: 24px 0 8px; }
    p { margin: 4px 0; }
    table { border-collapse: collapse; margin: 8px 0; }
    th, td { border: 1px solid #d0d7de; padding: 4px 10px; text-align: left; vertical-align: top; }
    th { background: #f6f8fa; font-weight: 600; }
    td.number { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    tr.total td { font-weight: 600; }
    tr.local td { background: #fff8e6; }
    .note { color: #59636e; }
    code { font-family: "JetBrains Mono", Menlo, Consolas, monospace; font-size: 12px; }
  """.trimIndent()

  fun write(file: Path, summary: Summary, logs: List<Path>) {
    file.createParentDirectories()
    file.bufferedWriter().use { it.writePage(summary, logs) }
  }

  private fun BufferedWriter.writePage(summary: Summary, logs: List<Path>) {
    write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
    write("<title>$TITLE</title>\n<style>\n$STYLE\n</style>\n</head>\n<body>\n")
    write("<h1>$TITLE</h1>\n")
    write("<p class=\"note\">Logs: ${logs.joinToString(", ") { "<code>${escape(it.fileName.toString())}</code>" }}</p>\n")

    writeSources(summary)
    writeLocalActions(summary)
    writeUploads(summary)
    writeTests(summary)
    writeLargestBlobs(summary)

    write("</body>\n</html>\n")
  }

  /**
   * Writes one table with the actions and the test actions for each source.
   */
  private fun BufferedWriter.writeSources(summary: Summary) {
    write("<h2>Actions</h2>\n")
    write("<table>\n<tr><th>Source</th><th>Actions</th><th>Test actions (TestRunner)</th></tr>\n")
    for (source in summary.shownSources()) {
      val rowClass = if (source == Source.LOCAL) " class=\"local\"" else ""
      write("<tr$rowClass><td>${source.text}</td>${number(summary.actions[source] ?: 0)}${number(summary.testActions[source] ?: 0)}</tr>\n")
    }
    write("<tr class=\"total\"><td>total</td>${number(summary.actions.values.sum())}${number(summary.testActions.values.sum())}</tr>\n")
    write("</table>\n")
    write("<p class=\"note\">The table does not count the hits of the local action cache. ")
    write("Such a hit starts no spawn, so the execution log does not hold it.</p>\n")
  }

  /**
   * Writes the number of actions run locally for each action kind.
   */
  private fun BufferedWriter.writeLocalActions(summary: Summary) {
    write("<h2>Actions run locally by action kind</h2>\n")
    if (summary.localActionsByMnemonic.isEmpty()) {
      write("<p class=\"note\">No actions run locally.</p>\n")
      return
    }
    write("<table>\n<tr><th>Action kind</th><th>Actions</th><th>Wall time</th></tr>\n")
    for (actions in summary.localActionsByMnemonic) {
      write("<tr><td>${escape(actions.mnemonic)}</td>${number(actions.count)}<td class=\"number\">${formatWallTime(actions.wallTimeNanos)}</td></tr>\n")
    }
    write("<tr class=\"total\"><td>total</td>${number(summary.localActionsByMnemonic.sumOf { it.count })}")
    write("<td class=\"number\">${formatWallTime(summary.localWallTimeNanos)}</td></tr>\n")
    write("</table>\n")
    write("<p class=\"note\">The wall time is the sum over the actions, not CPU time. ")
    write("Parallel actions can make it exceed the build time.</p>\n")
  }

  /**
   * Writes the blobs that the actions run locally can upload, for each action kind.
   */
  private fun BufferedWriter.writeUploads(summary: Summary) {
    write("<h2>Uploadable outputs of the actions run locally</h2>\n")
    write("<p>${blobCount(summary.uploads)}, ${formatSize(summary.uploads.values.sum())}</p>\n")
    if (summary.uploadsByMnemonic.isEmpty()) {
      return
    }
    write("<table>\n<tr><th>Action kind</th><th>Blobs</th><th>Size</th></tr>\n")
    for ((mnemonic, blobs) in summary.uploadsByMnemonic) {
      write("<tr><td>${escape(mnemonic)}</td>${number(blobs.size)}<td class=\"number\">${formatSize(blobs.values.sum())}</td></tr>\n")
    }
    write("</table>\n")
    write("<p class=\"note\">A blob that two action kinds share counts in both rows. The total counts it once.</p>\n")
  }

  /**
   * Writes one row for each test action.
   */
  private fun BufferedWriter.writeTests(summary: Summary) {
    write("<h2>Test actions</h2>\n")
    if (summary.tests.isEmpty()) {
      write("<p class=\"note\">No test actions.</p>\n")
      return
    }
    write("<table>\n<tr><th>Source</th><th>Uploadable</th><th>Target</th><th>Exit code</th></tr>\n")
    for (test in summary.tests) {
      val source = test.source()
      val rowClass = if (source == Source.LOCAL) " class=\"local\"" else ""
      val uploadClass = if (test.isUpload()) " class=\"number\"" else ""
      val target = escape(test.targetLabel.ifEmpty { "<no target>" })
      write("<tr$rowClass><td>${source.text}</td><td$uploadClass>${summary.uploadText(test)}</td><td><code>$target</code></td>${number(test.exitCode)}</tr>\n")
    }
    write("</table>\n")
    write("<p class=\"note\"><i>Uploadable</i> is the size of the distinct output blobs that the test action adds to the remote cache. ")
    write("Bazel uploads them only after a successful local run of a remote-cacheable test. ")
    write("A test with the <code>external</code> or <code>no-cache</code> tag is not remote-cacheable, ")
    write("and no test is under <code>--nocache_test_results</code>.</p>\n")
  }

  /**
   * Writes the largest blobs that the remote cache can hold, as in the text report of [BazelExecutionReport].
   */
  private fun BufferedWriter.writeLargestBlobs(summary: Summary) {
    write("<h2>Largest blobs</h2>\n")
    write("<p>${blobTotalText(summary.blobCount, summary.blobSizeBytes, summary.largestBlobs.size)}</p>\n")
    if (summary.largestBlobs.isEmpty()) {
      return
    }
    write("<table>\n<tr><th>Size</th><th>Paths</th><th>Action kind</th><th>Path</th><th>Target</th></tr>\n")
    for (blob in summary.largestBlobs) {
      val targets = blob.targets.joinToString("<br>") { "<code>${escape(it)}</code>" }
      write("<tr><td class=\"number\">${formatSize(blob.sizeBytes)}</td>${number(blob.paths.size)}<td>${escape(blob.mnemonics.joinToString(", "))}</td>")
      write("<td><code>${escape(blob.paths.first())}</code></td><td>$targets</td></tr>\n")
    }
    write("</table>\n")
    write("<p class=\"note\">The list holds the outputs of cache hits too. <i>Paths</i> counts the distinct paths that hold the same content.</p>\n")
  }

  private fun number(value: Int): String = "<td class=\"number\">$value</td>"

  private fun escape(text: String): String {
    val result = StringBuilder(text.length)
    for (c in text) {
      when (c) {
        '&' -> result.append("&amp;")
        '<' -> result.append("&lt;")
        '>' -> result.append("&gt;")
        '"' -> result.append("&quot;")
        '\'' -> result.append("&#39;")
        else -> result.append(c)
      }
    }
    return result.toString()
  }
}
