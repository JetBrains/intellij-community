// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

@ApiStatus.Internal
class Git(private val dir: Path) {
  companion object {
    /**
     * Whether the git tree-entry mode in [line]`[0, end)` (octal digits such as `100755`) has any
     * executable bit set. Reads the digits in place so [listTree] needs no mode string per entry.
     */
    @Suppress("GrazieInspection")
    fun isExecutableGitMode(line: CharSequence, end: Int): Boolean {
      var mode = 0
      for (i in 0 until end) {
        mode = (mode shl 3) or (line[i].code - '0'.code)
      }
      return (mode and 0b001_001_001) != 0 // any x bit
    }
  }

  fun log(commitCount: Int): List<String> {
    @Suppress("SpellCheckingInspection")
    return execute("git", "log", "-$commitCount", "--pretty=tformat:%H")
  }

  fun formatLatestCommit(format: String): String {
    return execute("git", "log", "--pretty=format:$format", "-n", "1").joinToString("\n")
  }

  data class Entry(val path: String, val isExecutable: Boolean)

  fun listTree(refSpec: String = "HEAD"): List<Entry> {
    return executeWithNullSeparatedOutput("git", "ls-tree", "-z", "-r", refSpec) { lines ->
      lines.map { line ->
        val tabIndex = line.indexOf('\t')
        // We only need the executable bit, so read it straight from the octal mode digits (before
        // the first space) — no mode string is allocated per entry.
        val isExecutable = isExecutableGitMode(line, line.indexOf(' '))
        // git ls-tree -z emits forward-slash, unquoted, repo-relative paths, so the substring is
        // already the invariant-separators path — no Path round-trip or extra string is needed.
        val path = line.substring(tabIndex + 1)
        Entry(path, isExecutable)
      }.toList()
    }
  }

  fun listStagingFiles(): List<String> {
    return executeWithNullSeparatedOutput("git", "ls-files", "-z") { it.toList() }
  }

  fun rm(files: List<Path>) {
    execute("git", "rm", *files.map { it.pathString }.toTypedArray())
  }

  fun add(files: List<Path>) {
    execute("git", "add", *files.map { it.pathString }.toTypedArray())
  }

  fun currentCommitShortHash(): String {
    val repoDirectory = dir.toAbsolutePath().toString()

    val lines = execute("git", "-c", "safe.directory=${repoDirectory}", "rev-parse", "--short=13", "HEAD")
    if (lines.size != 1) {
      throw IllegalStateException("Single line output is expected but got '$lines'")
    }
    val hash = lines.first().trim()
    if (hash.length != 13) {
      throw IllegalStateException("Short hash must be exactly 13 chars, but got '$hash'")
    }
    return hash
  }

  private fun maybeExecute(vararg command: String): ExecutionResult {
    val process = ProcessBuilder(*command).directory(@Suppress("IO_FILE_USAGE")dir.toFile()).start()
    var output = process.inputStream.bufferedReader().use {
      it.lines().map { line -> line.trim() }.toList()
    }
    if (!process.waitFor(1, TimeUnit.MINUTES)) {
      process.destroyForcibly().waitFor()
      throw IllegalStateException("Cannot execute '${command.joinToString(" ")}': 1 minute timeout")
    }
    if (process.exitValue() != 0) {
      output = listOf(process.errorStream.bufferedReader().use { it.readText() }) + output
    }
    return ExecutionResult(process.exitValue(), output)
  }

  /**
   * Runs [command] and streams its NUL-separated stdout to [handler] as a lazy [Sequence] of
   * records, decoding each record (UTF-8) on demand. Unlike reading the whole output into a single
   * byte array / String and splitting it, this never materializes the full output: for `git ls-tree
   * -z` over the whole VCS tree that otherwise costs a ~200 MB transient buffer plus an equally
   * large String. Only the records the [handler] retains stay in memory.
   *
   * [handler] must fully consume the sequence before returning (e.g. via `toList()`): it is
   * single-pass and tied to the process stdout, which is closed once [handler] returns.
   */
  private fun <T> executeWithNullSeparatedOutput(vararg command: String, handler: (Sequence<String>) -> T): T {
    val process = ProcessBuilder(*command)
      .redirectError(Redirect.INHERIT)
      .directory(@Suppress("IO_FILE_USAGE")dir.toFile())
      .start()
    process.outputStream.close()
    try {
      val result = process.inputStream.use { handler(nullSeparatedSequence(it)) }

      if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroyForcibly().waitFor()
        throw IllegalStateException("Cannot execute ${command.toList()}: 5 minutes timeout")
      }
      val exitCode = process.exitValue()
      if (exitCode != 0) {
        throw IllegalStateException("Cannot execute ${command.toList()}: exit code $exitCode")
      }
      return result
    }
    finally {
      process.destroyForcibly()
    }
  }

  /**
   * Lazily splits [input] on the NUL byte, yielding each non-empty record decoded as UTF-8.
   * Reads in fixed-size chunks and only buffers a record that straddles a chunk boundary, so peak
   * memory stays at one chunk regardless of the total output size. Splitting on the `0x00` byte is
   * equivalent to splitting the decoded text on `U+0000`, since `0x00` never occurs inside a
   * multibyte UTF-8 sequence; each record is decoded from contiguous bytes, so sequences are never
   * decoded split.
   */
  private fun nullSeparatedSequence(input: InputStream): Sequence<String> = sequence {
    val buffer = ByteArray(64 * 1024)
    val carry = ByteArrayOutputStream()
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      var segmentStart = 0
      for (i in 0 until read) {
        if (buffer[i].toInt() != 0) continue
        if (carry.size() == 0) {
          if (i > segmentStart) {
            yield(String(buffer, segmentStart, i - segmentStart, Charsets.UTF_8))
          }
        }
        else {
          carry.write(buffer, segmentStart, i - segmentStart)
          yield(String(carry.toByteArray(), Charsets.UTF_8))
          carry.reset()
        }
        segmentStart = i + 1
      }
      if (segmentStart < read) {
        carry.write(buffer, segmentStart, read - segmentStart)
      }
    }
    if (carry.size() > 0) {
      yield(String(carry.toByteArray(), Charsets.UTF_8))
    }
  }

  private fun execute(vararg command: String): List<String> {
    val result = maybeExecute(*command)
    if (result.exitCode != 0) {
      throw IllegalStateException("${command.toList()} failed with $result.exitCode:\n${result.output.joinToString("\n")}")
    }
    return result.output
  }
}

private data class ExecutionResult(@JvmField val exitCode: Int, @JvmField val output: List<String>)
