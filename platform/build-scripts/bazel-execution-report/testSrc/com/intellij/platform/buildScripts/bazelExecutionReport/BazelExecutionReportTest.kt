// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.fileEntry
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.spawnEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
      spawnEntry(mnemonic = "Javac", targetLabel = "//a", runner = "linux-sandbox", remoteCacheable = true, outputIds = listOf(1)),
      spawnEntry(mnemonic = "Javac", targetLabel = "//b", runner = "remote cache hit", cacheHit = true, remoteCacheable = true, outputIds = listOf(2)),
    )
    // The entry IDs of a log are valid only in that log, so this log uses ID 1 again.
    TestExecutionLog.write(
      logs.resolve("test.exec.log.zst"),
      fileEntry(id = 1, path = "bazel-out/k8-fastbuild/testlogs/t/test.xml", hash = "t", sizeBytes = MB),
      spawnEntry(mnemonic = "TestRunner", targetLabel = "//t", runner = "linux-sandbox", remoteCacheable = true, outputIds = listOf(1)),
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
      // The actions run locally for each action kind. Equal counts sort by the action kind.
      "<tr><th>Action kind</th><th>Actions</th></tr>\n" +
      "<tr><td>Javac</td><td class=\"number\">1</td></tr>\n" +
      "<tr><td>TestRunner</td><td class=\"number\">1</td></tr>\n",
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

  private companion object {
    const val MB = 1024L * 1024
  }
}
