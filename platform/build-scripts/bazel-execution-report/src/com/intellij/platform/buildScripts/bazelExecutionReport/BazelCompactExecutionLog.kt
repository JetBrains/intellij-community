// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.github.luben.zstd.ZstdInputStream
import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED
import com.google.protobuf.WireFormat.WIRETYPE_VARINT
import java.nio.file.Path
import kotlin.io.path.inputStream

/**
 * Reads the compact Bazel execution log that `--execution_log_compact_file` writes.
 *
 * The log is a zstd stream of length-delimited `ExecLogEntry` messages from Bazel's `src/main/protobuf/spawn.proto`.
 * A spawn refers to its outputs by entry ID, and Bazel writes each referenced entry before the spawn.
 *
 * The reader makes two passes, so it keeps only the entries that it needs:
 * 1. it reads the spawns and collects the output IDs to resolve.
 * 2. it reads the `File` and `Directory` entries with those IDs.
 *
 * The reader decodes only the fields that the report uses. [CodedInputStream] decodes the wire format,
 * so the reader needs no generated protobuf code. The reader matches each field by its tag, which holds the field number
 * and the wire type, and skips a field of an unknown tag.
 */
internal object BazelCompactExecutionLog {
  // The zstd frame magic number 0xFD2FB528, in the little-endian byte order of the file.
  private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())

  // The tags of `ExecLogEntry`.
  private const val ENTRY_ID = (1 shl 3) or WIRETYPE_VARINT
  private const val ENTRY_FILE = (3 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val ENTRY_DIRECTORY = (4 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val ENTRY_SPAWN = (7 shl 3) or WIRETYPE_LENGTH_DELIMITED

  // The tags of `ExecLogEntry.Spawn`.
  private const val SPAWN_OUTPUTS = (6 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val SPAWN_TARGET_LABEL = (7 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val SPAWN_MNEMONIC = (8 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val SPAWN_EXIT_CODE = (9 shl 3) or WIRETYPE_VARINT
  private const val SPAWN_RUNNER = (11 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val SPAWN_CACHE_HIT = (12 shl 3) or WIRETYPE_VARINT
  private const val SPAWN_REMOTE_CACHEABLE = (15 shl 3) or WIRETYPE_VARINT

  // The tag of `ExecLogEntry.Output.output_id`.
  private const val OUTPUT_ID = (5 shl 3) or WIRETYPE_VARINT

  // The tags of `ExecLogEntry.File` and `ExecLogEntry.Directory`. Both messages hold the path in field 1.
  private const val PATH = (1 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val FILE_DIGEST = (2 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val DIRECTORY_FILES = (2 shl 3) or WIRETYPE_LENGTH_DELIMITED

  // The tags of `Digest`.
  private const val DIGEST_HASH = (1 shl 3) or WIRETYPE_LENGTH_DELIMITED
  private const val DIGEST_SIZE_BYTES = (2 shl 3) or WIRETYPE_VARINT

  private const val BUFFER_SIZE = 1 shl 16

  /**
   * Reports whether the file starts with the zstd magic number. The JSON log starts with text.
   */
  fun isCompact(file: Path): Boolean = file.inputStream()
    .use { it.readNBytes(ZSTD_MAGIC.size) }
    .contentEquals(ZSTD_MAGIC)

  /**
   * Reads every spawn of the log. [filterOutputs] selects the spawns whose outputs the reader resolves.
   * The other spawns come back with no outputs.
   */
  fun readSpawns(
    file: Path,
    filterOutputs: (BazelExecutionReport.Spawn) -> Boolean,
  ): List<BazelExecutionReport.Spawn> {
    val spawns = ArrayList<BazelExecutionReport.Spawn>()
    // The output IDs to resolve, in the order of `spawns`. A spawn that the caller does not select has none.
    val spawnOutputIds = ArrayList<List<Int>>()
    val neededIds = HashSet<Int>()
    // One buffer for the output IDs of every spawn, so that a spawn that the caller does not select allocates no list.
    val outputIds = ArrayList<Int>()
    forEachEntry(file) { input ->
      input.forEachField { tag ->
        if (tag == ENTRY_SPAWN) {
          outputIds.clear()
          val spawn = input.readMessage { readSpawn(input, outputIds) }
          spawns.add(spawn)
          if (filterOutputs(spawn)) {
            neededIds.addAll(outputIds)
            spawnOutputIds.add(outputIds.toList())
          }
          else {
            spawnOutputIds.add(emptyList())
          }
        }
        else {
          input.skipField(tag)
        }
      }
    }

    val outputs = if (neededIds.isEmpty()) emptyMap() else readOutputs(file, neededIds)
    spawns.forEachIndexed { index, spawn ->
      spawn.outputs = spawnOutputIds[index].flatMap { outputs[it].orEmpty() }
    }
    return spawns
  }

  /**
   * Reads the `File` and `Directory` entries with the given IDs, and keeps the files that have a digest.
   *
   * A directory gives one output for each of its files. An output ID that refers to an unresolved symlink finds no entry here.
   * The log may omit the digest of an empty file. Neither case is a blob that takes space.
   */
  private fun readOutputs(file: Path, ids: Set<Int>): Map<Int, List<BazelExecutionReport.Output>> {
    val result = HashMap<Int, List<BazelExecutionReport.Output>>()
    forEachEntry(file) { input ->
      var id = 0
      var type = 0
      var body: ByteString? = null
      input.forEachField { tag ->
        when {
          tag == ENTRY_ID -> id = input.readUInt32()
          // Bazel writes the ID first, so the reader usually skips the body of an entry that it does not need.
          (tag == ENTRY_FILE || tag == ENTRY_DIRECTORY) && (id == 0 || id in ids) -> {
            type = tag
            body = input.readBytes()
          }
          else -> input.skipField(tag)
        }
      }
      val bodyInput = body?.takeIf { id in ids }?.newCodedInput() ?: return@forEachEntry
      val files = if (type == ENTRY_FILE) listOf(readFile(bodyInput)) else readDirectory(bodyInput)
      result[id] = files.filter { it.hash.isNotEmpty() }
    }
    return result
  }

  /**
   * Reads one `Spawn` message, and adds its output IDs to [outputIds].
   * An output that Bazel could not record holds a path and no ID, so it adds no ID.
   *
   * Protobuf drops a field that holds the default value, so a missing field keeps its default.
   */
  private fun readSpawn(input: CodedInputStream, outputIds: MutableList<Int>): BazelExecutionReport.Spawn {
    var mnemonic = ""
    var targetLabel = ""
    var runner = ""
    var exitCode = 0
    var cacheHit = false
    var remoteCacheable = false
    input.forEachField { tag ->
      when (tag) {
        SPAWN_OUTPUTS -> input.readMessage {
          input.forEachField {
            if (it == OUTPUT_ID) outputIds.add(input.readUInt32()) else input.skipField(it)
          }
        }
        SPAWN_TARGET_LABEL -> targetLabel = input.readString()
        SPAWN_MNEMONIC -> mnemonic = input.readString()
        SPAWN_EXIT_CODE -> exitCode = input.readInt32()
        SPAWN_RUNNER -> runner = input.readString()
        SPAWN_CACHE_HIT -> cacheHit = input.readBool()
        SPAWN_REMOTE_CACHEABLE -> remoteCacheable = input.readBool()
        else -> input.skipField(tag)
      }
    }
    return BazelExecutionReport.Spawn(
      mnemonic = mnemonic,
      targetLabel = targetLabel,
      runner = runner,
      exitCode = exitCode,
      cacheHit = cacheHit,
      remoteCacheable = remoteCacheable,
    )
  }

  /**
   * Reads one `Directory` message. Bazel writes the path of each file relative to the directory.
   */
  private fun readDirectory(input: CodedInputStream): List<BazelExecutionReport.Output> {
    var path = ""
    val files = ArrayList<BazelExecutionReport.Output>()
    input.forEachField { tag ->
      when (tag) {
        PATH -> path = input.readString()
        DIRECTORY_FILES -> input.readMessage { files.add(readFile(input)) }
        else -> input.skipField(tag)
      }
    }
    // The reader does not depend on the field order, so it joins the paths after the loop.
    return files.map { BazelExecutionReport.Output(hash = it.hash, path = "$path/${it.path}", sizeBytes = it.sizeBytes) }
  }

  private fun readFile(input: CodedInputStream): BazelExecutionReport.Output {
    var path = ""
    var hash = ""
    var sizeBytes = 0L
    input.forEachField { tag ->
      when (tag) {
        PATH -> path = input.readString()
        FILE_DIGEST -> input.readMessage {
          input.forEachField {
            when (it) {
              DIGEST_HASH -> hash = input.readString()
              DIGEST_SIZE_BYTES -> sizeBytes = input.readInt64()
              else -> input.skipField(it)
            }
          }
        }
        else -> input.skipField(tag)
      }
    }
    return BazelExecutionReport.Output(hash = hash, path = path, sizeBytes = sizeBytes)
  }

  /**
   * Calls [action] for each entry of the log. [action] gets the stream with a limit at the end of the entry.
   *
   * [CodedInputStream] fails after 2 GB of input by default, and a log can hold more. So the reader resets the size counter
   * after each entry.
   */
  private inline fun forEachEntry(file: Path, action: (CodedInputStream) -> Unit) {
    ZstdInputStream(file.inputStream()).use { stream ->
      val input = CodedInputStream.newInstance(stream, BUFFER_SIZE)
      while (!input.isAtEnd) {
        input.readMessage { action(input) }
        input.resetSizeCounter()
      }
    }
  }

  /**
   * Calls [read] with the tag of each field up to the current limit. [read] must read the value, or skip it with [CodedInputStream.skipField].
   */
  private inline fun CodedInputStream.forEachField(read: (tag: Int) -> Unit) {
    while (true) {
      val tag = readTag()
      if (tag == 0) {
        return
      }
      read(tag)
    }
  }

  /**
   * Reads a length-delimited message: calls [read] with a limit at the end of the message, and skips what [read] leaves.
   */
  private inline fun <T> CodedInputStream.readMessage(read: () -> T): T {
    val oldLimit = pushLimit(readRawVarint32())
    val result = read()
    skipRawBytes(bytesUntilLimit)
    popLimit(oldLimit)
    return result
  }
}
