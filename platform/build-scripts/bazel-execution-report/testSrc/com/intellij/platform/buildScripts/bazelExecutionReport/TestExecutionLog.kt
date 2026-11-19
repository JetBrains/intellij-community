// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.github.luben.zstd.ZstdOutputStream
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

/**
 * Writes compact Bazel execution logs for tests, so the repository holds no binary log.
 *
 * The functions encode `ExecLogEntry` messages with protobuf-java, and [write] compresses them with zstd.
 * The field numbers come from Bazel's `src/main/protobuf/spawn.proto`, not from [BazelCompactExecutionLog].
 * So a wrong field number in the reader fails a test.
 */
internal object TestExecutionLog {
  // The field numbers of `ExecLogEntry`.
  const val ENTRY_ID = 1
  const val ENTRY_INVOCATION = 2
  const val ENTRY_FILE = 3
  const val ENTRY_DIRECTORY = 4
  const val ENTRY_UNRESOLVED_SYMLINK = 5
  const val ENTRY_SPAWN = 7

  // The field numbers of `ExecLogEntry.File`, `ExecLogEntry.Directory`, and `ExecLogEntry.UnresolvedSymlink`.
  const val FILE_PATH = 1
  const val FILE_DIGEST = 2
  const val DIRECTORY_PATH = 1
  const val DIRECTORY_FILES = 2
  const val UNRESOLVED_SYMLINK_PATH = 1
  const val UNRESOLVED_SYMLINK_TARGET_PATH = 2

  // The field numbers of `Digest`.
  const val DIGEST_HASH = 1
  const val DIGEST_SIZE_BYTES = 2

  // The field numbers of `ExecLogEntry.Output`.
  const val OUTPUT_INVALID_OUTPUT_PATH = 4
  const val OUTPUT_ID = 5

  // The field numbers of `ExecLogEntry.Spawn`.
  const val SPAWN_ARGS = 1
  const val SPAWN_OUTPUTS = 6
  const val SPAWN_TARGET_LABEL = 7
  const val SPAWN_MNEMONIC = 8
  const val SPAWN_EXIT_CODE = 9
  const val SPAWN_RUNNER = 11
  const val SPAWN_CACHE_HIT = 12
  const val SPAWN_REMOTE_CACHEABLE = 15
  const val SPAWN_TIMEOUT_MILLIS = 17
  const val SPAWN_METRICS = 18

  // The field numbers of `SpawnMetrics`.
  const val METRICS_TOTAL_TIME = 1
  const val METRICS_QUEUE_TIME = 5
  const val METRICS_EXECUTION_WALL_TIME = 8

  // The field numbers of `google.protobuf.Duration`.
  const val DURATION_SECONDS = 1
  const val DURATION_NANOS = 2

  /**
   * Writes the entries to [log] as a compact execution log: a zstd stream of length-delimited `ExecLogEntry` messages.
   */
  fun write(log: Path, vararg entries: ByteArray): Path {
    ZstdOutputStream(log.outputStream()).use { out ->
      val coded = CodedOutputStream.newInstance(out)
      for (entry in entries) {
        coded.writeUInt32NoTag(entry.size)
        coded.writeRawBytes(entry)
      }
      coded.flush()
    }
    return log
  }

  fun fileEntry(id: Int, path: String, hash: String?, sizeBytes: Long): ByteArray = message {
    writeUInt32(ENTRY_ID, id)
    writeByteArray(ENTRY_FILE, file(path, hash, sizeBytes))
  }

  fun directoryEntry(id: Int, path: String, vararg files: ByteArray): ByteArray = message {
    writeUInt32(ENTRY_ID, id)
    writeByteArray(ENTRY_DIRECTORY, message {
      writeString(DIRECTORY_PATH, path)
      for (file in files) {
        writeByteArray(DIRECTORY_FILES, file)
      }
    })
  }

  fun unresolvedSymlinkEntry(id: Int, path: String, targetPath: String): ByteArray = message {
    writeUInt32(ENTRY_ID, id)
    writeByteArray(ENTRY_UNRESOLVED_SYMLINK, message {
      writeString(UNRESOLVED_SYMLINK_PATH, path)
      writeString(UNRESOLVED_SYMLINK_TARGET_PATH, targetPath)
    })
  }

  fun spawnEntry(
    mnemonic: String,
    targetLabel: String = "",
    runner: String = "",
    exitCode: Int = 0,
    cacheHit: Boolean = false,
    remoteCacheable: Boolean = false,
    outputIds: List<Int> = emptyList(),
    extraFields: CodedOutputStream.() -> Unit = {},
  ): ByteArray = message {
    writeByteArray(ENTRY_SPAWN, message {
      for (id in outputIds) {
        writeByteArray(SPAWN_OUTPUTS, message { writeUInt32(OUTPUT_ID, id) })
      }
      writeString(SPAWN_TARGET_LABEL, targetLabel)
      writeString(SPAWN_MNEMONIC, mnemonic)
      writeInt32(SPAWN_EXIT_CODE, exitCode)
      writeString(SPAWN_RUNNER, runner)
      writeBool(SPAWN_CACHE_HIT, cacheHit)
      writeBool(SPAWN_REMOTE_CACHEABLE, remoteCacheable)
      extraFields()
    })
  }

  /**
   * Writes a `SpawnMetrics` message into a spawn, for [spawnEntry] `extraFields`. A null duration leaves its field out.
   */
  fun CodedOutputStream.writeMetrics(executionWallTimeMillis: Long? = null, totalTimeMillis: Long? = null, queueTimeMillis: Long? = null) {
    writeByteArray(SPAWN_METRICS, message {
      if (totalTimeMillis != null) {
        writeByteArray(METRICS_TOTAL_TIME, duration(totalTimeMillis))
      }
      if (queueTimeMillis != null) {
        writeByteArray(METRICS_QUEUE_TIME, duration(queueTimeMillis))
      }
      if (executionWallTimeMillis != null) {
        writeByteArray(METRICS_EXECUTION_WALL_TIME, duration(executionWallTimeMillis))
      }
    })
  }

  private fun duration(millis: Long): ByteArray = message {
    writeInt64(DURATION_SECONDS, millis / 1000)
    writeInt32(DURATION_NANOS, ((millis % 1000) * 1_000_000).toInt())
  }

  /**
   * Encodes an `ExecLogEntry.File` message. A null [hash] gives a file with no digest.
   */
  fun file(path: String, hash: String?, sizeBytes: Long): ByteArray = message {
    writeString(FILE_PATH, path)
    if (hash != null) {
      writeByteArray(FILE_DIGEST, message {
        writeString(DIGEST_HASH, hash)
        writeInt64(DIGEST_SIZE_BYTES, sizeBytes)
      })
    }
  }

  /**
   * Encodes one message with the fields that [write] writes.
   */
  fun message(write: CodedOutputStream.() -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(bytes)
    coded.write()
    coded.flush()
    return bytes.toByteArray()
  }
}
