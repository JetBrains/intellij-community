// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.github.luben.zstd.ZstdOutputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.InvalidProtocolBufferException
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.ENTRY_FILE
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.ENTRY_ID
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.ENTRY_INVOCATION
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.OUTPUT_INVALID_OUTPUT_PATH
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.SPAWN_ARGS
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.SPAWN_METRICS
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.SPAWN_OUTPUTS
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.SPAWN_TIMEOUT_MILLIS
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.directoryEntry
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.file
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.fileEntry
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.message
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.spawnEntry
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.unresolvedSymlinkEntry
import com.intellij.platform.buildScripts.bazelExecutionReport.TestExecutionLog.writeMetrics
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

/**
 * Tests [BazelCompactExecutionLog] on logs that each test writes itself with [TestExecutionLog].
 */
class BazelCompactExecutionLogTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `resolves the file and directory outputs of a selected spawn`() {
    val log = writeLog(
      fileEntry(id = 1, path = "bazel-out/bin/a.jar", hash = "h1", sizeBytes = 10),
      directoryEntry(id = 2, path = "bazel-out/bin/tree", file("x.txt", "h2", 20), file("sub/y", "h3", 30)),
      spawnEntry(mnemonic = "Javac", targetLabel = "//a:b", runner = "linux-sandbox", remoteCacheable = true, outputIds = listOf(1, 2)),
    )

    val spawn = BazelCompactExecutionLog.readSpawns(log) { true }.single()

    assertThat(spawn.mnemonic).isEqualTo("Javac")
    assertThat(spawn.targetLabel).isEqualTo("//a:b")
    assertThat(spawn.runner).isEqualTo("linux-sandbox")
    assertThat(spawn.exitCode).isZero()
    assertThat(spawn.cacheHit).isFalse()
    assertThat(spawn.remoteCacheable).isTrue()
    assertThat(spawn.outputs.map { listOf(it.path, it.hash, it.sizeBytes) }).containsExactly(
      listOf("bazel-out/bin/a.jar", "h1", 10L),
      listOf("bazel-out/bin/tree/x.txt", "h2", 20L),
      listOf("bazel-out/bin/tree/sub/y", "h3", 30L),
    )
  }

  @Test
  fun `returns a spawn that the caller does not select, with no outputs`() {
    val log = writeLog(
      fileEntry(id = 1, path = "bazel-out/bin/a.jar", hash = "h1", sizeBytes = 10),
      spawnEntry(mnemonic = "Javac", outputIds = listOf(1)),
      spawnEntry(mnemonic = "TestRunner", outputIds = listOf(1)),
    )

    val spawns = BazelCompactExecutionLog.readSpawns(log) { it.mnemonic == "TestRunner" }

    assertThat(spawns.map { it.mnemonic }).containsExactly("Javac", "TestRunner")
    assertThat(spawns[0].outputs).isEmpty()
    assertThat(spawns[1].outputs.map { it.path }).containsExactly("bazel-out/bin/a.jar")
  }

  @Test
  fun `drops an output with no digest, an unknown ID, an unresolved symlink, and an invalid output path`() {
    val log = writeLog(
      fileEntry(id = 1, path = "bazel-out/bin/empty", hash = null, sizeBytes = 0),
      unresolvedSymlinkEntry(id = 2, path = "bazel-out/bin/link", targetPath = "missing"),
      spawnEntry(mnemonic = "Genrule", outputIds = listOf(1, 2, 42)) {
        writeByteArray(SPAWN_OUTPUTS, message { writeString(OUTPUT_INVALID_OUTPUT_PATH, "bazel-out/bin/broken") })
      },
    )

    val spawn = BazelCompactExecutionLog.readSpawns(log) { true }.single()

    assertThat(spawn.outputs).isEmpty()
  }

  @Test
  fun `keeps both outputs of the same content under two IDs`() {
    val log = writeLog(
      fileEntry(id = 1, path = "bazel-out/bin/a.jar", hash = "same", sizeBytes = 10),
      fileEntry(id = 2, path = "bazel-out/bin/b.jar", hash = "same", sizeBytes = 10),
      spawnEntry(mnemonic = "Javac", outputIds = listOf(1, 2)),
    )

    val spawn = BazelCompactExecutionLog.readSpawns(log) { true }.single()

    assertThat(spawn.outputs.map { it.path }).containsExactly("bazel-out/bin/a.jar", "bazel-out/bin/b.jar")
  }

  @Test
  fun `reads a cache hit and a negative exit code`() {
    val log = writeLog(
      spawnEntry(mnemonic = "TestRunner", runner = "remote cache hit", cacheHit = true, exitCode = -1),
    )

    val spawn = BazelCompactExecutionLog.readSpawns(log) { true }.single()

    assertThat(spawn.runner).isEqualTo("remote cache hit")
    assertThat(spawn.cacheHit).isTrue()
    assertThat(spawn.exitCode).isEqualTo(-1)
  }

  @Test
  fun `skips unknown fields and entries, and does not depend on the field order`() {
    val log = writeLog(
      message { writeByteArray(ENTRY_INVOCATION, message { writeString(1, "SHA-256") }) },
      // The ID comes after the body here. Bazel writes it first.
      message {
        writeByteArray(ENTRY_FILE, file("bazel-out/bin/a.jar", "h1", 10))
        writeUInt32(ENTRY_ID, 7)
      },
      spawnEntry(mnemonic = "Javac", outputIds = listOf(7)) {
        writeString(SPAWN_ARGS, "javac")
        writeInt64(SPAWN_TIMEOUT_MILLIS, 60_000)
        writeByteArray(SPAWN_METRICS, message { writeInt64(1, 5) })
        // Spawn has no field of these wire types, so the test adds unknown ones.
        writeFixed64(1000, 1)
        writeFixed32(1001, 2)
      },
    )

    val spawn = BazelCompactExecutionLog.readSpawns(log) { true }.single()

    assertThat(spawn.mnemonic).isEqualTo("Javac")
    assertThat(spawn.outputs.map { it.path }).containsExactly("bazel-out/bin/a.jar")
  }

  @Test
  fun `reads the execution wall time, falls back to the total time, and reads no metrics as zero`() {
    val log = writeLog(
      spawnEntry(mnemonic = "Both") { writeMetrics(executionWallTimeMillis = 1_250, totalTimeMillis = 9_000) },
      spawnEntry(mnemonic = "TotalOnly") { writeMetrics(totalTimeMillis = 2_500) },
      spawnEntry(mnemonic = "None"),
    )

    val spawns = BazelCompactExecutionLog.readSpawns(log) { true }

    assertThat(spawns.map { it.mnemonic to it.wallTimeNanos }).containsExactly(
      "Both" to 1_250_000_000L,
      "TotalOnly" to 2_500_000_000L,
      "None" to 0L,
    )
  }

  @Test
  fun `reads the queue time, and reads no queue time as zero`() {
    val log = writeLog(
      spawnEntry(mnemonic = "Queued") { writeMetrics(executionWallTimeMillis = 1_000, queueTimeMillis = 3_750) },
      spawnEntry(mnemonic = "NotQueued") { writeMetrics(executionWallTimeMillis = 1_000) },
      spawnEntry(mnemonic = "None"),
    )

    val spawns = BazelCompactExecutionLog.readSpawns(log) { true }

    assertThat(spawns.map { it.mnemonic to it.queueTimeNanos }).containsExactly(
      "Queued" to 3_750_000_000L,
      "NotQueued" to 0L,
      "None" to 0L,
    )
  }

  @Test
  fun `reads an empty log`() {
    assertThat(BazelCompactExecutionLog.readSpawns(writeLog()) { true }).isEmpty()
  }

  @Test
  fun `fails on a log that ends inside an entry`() {
    val log = tempDir.resolve("truncated.exec.log.zst")
    ZstdOutputStream(log.outputStream()).use { out ->
      val coded = CodedOutputStream.newInstance(out)
      coded.writeUInt32NoTag(100)
      coded.writeRawBytes(byteArrayOf(1, 2, 3))
      coded.flush()
    }

    assertThatThrownBy { BazelCompactExecutionLog.readSpawns(log) { true } }
      .isInstanceOf(InvalidProtocolBufferException::class.java)
  }

  @Test
  fun `recognizes a compact log by its zstd magic number`() {
    val text = tempDir.resolve("exec.json")
    text.writeText("{}")

    assertThat(BazelCompactExecutionLog.isCompact(writeLog(spawnEntry(mnemonic = "Javac")))).isTrue()
    assertThat(BazelCompactExecutionLog.isCompact(text)).isFalse()
  }

  private fun writeLog(vararg entries: ByteArray): Path = TestExecutionLog.write(tempDir.resolve("exec.log.zst"), *entries)
}
