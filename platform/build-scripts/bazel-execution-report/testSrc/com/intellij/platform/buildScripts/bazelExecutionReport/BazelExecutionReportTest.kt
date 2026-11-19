// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.reportStatistics
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.fileEntry
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.spawnEntry
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.writeMetrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.readText

class BazelExecutionReportTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `reports the actions, the tests, and the largest blobs of a log directory`() {
    val logs = tempDir.resolve("logs").createDirectory()
    TestExecutionLog.write(
      logs.resolve("build.exec.log.zst"),
      fileEntry(id = 1, path = "bazel-out/k8-fastbuild/bin/a.jar", hash = "a", sizeBytes = 3 * MB),
      fileEntry(id = 2, path = "bazel-out/k8-fastbuild/bin/b.jar", hash = "b", sizeBytes = 2 * MB),
      spawnEntry(mnemonic = "Javac", targetLabel = "//a", runner = "linux-sandbox", remoteCacheable = true, outputIds = listOf(1)) {
        writeMetrics(executionWallTimeMillis = 2_340, queueTimeMillis = 1_500)
      },
      // A cache hit adds no wall time to the actions run locally.
      spawnEntry(mnemonic = "Javac", targetLabel = "//b", runner = "remote cache hit", cacheHit = true, remoteCacheable = true, outputIds = listOf(2)) {
        writeMetrics(totalTimeMillis = 500)
      },
    )
    // The entry IDs of a log are valid only in that log, so this log uses ID 1 again.
    TestExecutionLog.write(
      logs.resolve("test.exec.log.zst"),
      fileEntry(id = 1, path = "bazel-out/k8-fastbuild/testlogs/t/test.xml", hash = "t", sizeBytes = MB),
      spawnEntry(mnemonic = "TestRunner", targetLabel = "//t", runner = "linux-sandbox", remoteCacheable = true, outputIds = listOf(1)) {
        writeMetrics(executionWallTimeMillis = 10_000)
      },
      spawnEntry(mnemonic = "TestRunner", targetLabel = "//u", runner = "disk cache hit", cacheHit = true, remoteCacheable = true),
    )
    val html = tempDir.resolve("report.html")

    BazelExecutionReport.main(arrayOf("$logs", "--html-report-path", "$html"))

    val page = html.readText()
    assertThat(page).contains(
      "build.exec.log.zst", "test.exec.log.zst",
      // The actions and the test actions for each source.
      "<tr><td>remote cache hit</td><td class=\"number\">1</td><td class=\"number\">0</td></tr>",
      "<tr><td>disk cache hit</td><td class=\"number\">1</td><td class=\"number\">1</td></tr>",
      "<tr class=\"local\"><td>run locally</td><td class=\"number\">2</td><td class=\"number\">1</td></tr>",
      "<tr class=\"total\"><td>total</td><td class=\"number\">4</td><td class=\"number\">2</td></tr>",
      "The table does not count the hits of the local action cache.",
      // The actions run locally for each action kind, with their wall time and queue time. Equal counts sort by the action kind.
      "<tr><th>Action kind</th><th>Actions</th><th>Wall time</th><th>Queue time</th></tr>\n" +
      "<tr><td>Javac</td><td class=\"number\">1</td><td class=\"number\">2.3 s</td><td class=\"number\">1.5 s</td></tr>\n" +
      "<tr><td>TestRunner</td><td class=\"number\">1</td><td class=\"number\">10.0 s</td><td class=\"number\">0.0 s</td></tr>\n" +
      "<tr class=\"total\"><td>total</td><td class=\"number\">2</td><td class=\"number\">12.3 s</td><td class=\"number\">1.5 s</td></tr>\n",
      // The uploadable outputs of the actions run locally.
      "2 blobs, 4 mb",
      "<tr><td>Javac</td><td class=\"number\">1</td><td class=\"number\">3 mb</td></tr>",
      // The test actions.
      "<td>run locally</td><td class=\"number\">1 mb</td>",
      "<td>disk cache hit</td><td>none, cache hit</td>",
      // The largest blobs, with the outputs of cache hits too.
      "3 blobs, 6 mb total",
      "k8-fastbuild/bin/a.jar",
      "k8-fastbuild/bin/b.jar",
    )
    assertThat(page).doesNotContain("bazel-out/")
  }

  @Test
  fun `writes the queue time of each action kind run locally as a TeamCity statistic value`() {
    val spawns = listOf(
      BazelExecutionReport.Spawn("IjPluginPackaging", "//a", "worker", 0, cacheHit = false, remoteCacheable = true, queueTimeNanos = 1_200_000_000L),
      BazelExecutionReport.Spawn("IjPluginPackaging", "//b", "worker", 0, cacheHit = false, remoteCacheable = true, queueTimeNanos = 300_000_000L),
      BazelExecutionReport.Spawn("Javac", "//c", "linux-sandbox", 0, cacheHit = false, remoteCacheable = true),
      // A cache hit gives no statistic value.
      BazelExecutionReport.Spawn("Rustc", "//d", "remote cache hit", 0, cacheHit = true, remoteCacheable = true, queueTimeNanos = 5_000_000_000L),
    )
    val text = StringWriter()

    text.buffered().use { it.reportStatistics(BazelExecutionReport.Summary(spawns, largestBlobsCount = 0, blobs = emptyList())) }

    assertThat(text.toString().lines().filter { it.isNotEmpty() }).containsExactly(
      "##teamcity[buildStatisticValue key='bazel.queueTimeMs.IjPluginPackaging' value='1500']",
      "##teamcity[buildStatisticValue key='bazel.queueTimeMs.Javac' value='0']",
    )
  }

  private companion object {
    const val MB = 1024L * 1024
  }
}
