// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.bazelExecutionReport

import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.formatSize
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.limitText
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.reportBuildProblems
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.reportSummary
import com.intellij.platform.buildScripts.bazelExecutionReport.BazelExecutionReportOutput.reportViolations
import org.jetbrains.annotations.ApiStatus
import java.io.BufferedWriter
import java.nio.file.Path
import java.util.TreeSet
import java.io.IOException

/**
 * Reports on the spawns of compact Bazel execution logs: where the actions came from, and the largest blobs that the remote cache can hold.
 *
 * Write the logs first:
 * ```
 * ./bazel-build-all.cmd --execution_log_compact_file=<file>
 * ```
 *
 * Then read them:
 * ```
 * ./bazel.cmd run //platform/build-scripts/bazel-execution-report:bazel_execution_report -- <file or directory> [--largest-blobs-count N] [--blob-size-limit-mb N] [--blob-size-limit-extensions LIST] [--html-report-path FILE]
 * ```
 *
 * The tool reads one log, or every `*.zst` file directly in a directory, and merges the logs into one report.
 * For example, write the log of each Bazel command of a CI build to one directory.
 * The entry IDs of a log are valid only in that log, so the tool resolves each log on its own and then joins the spawns.
 * A blob that two logs share counts once, because the report identifies a blob by its hash.
 *
 * [BazelCompactExecutionLog] streams the log, so the log size does not limit the tool.
 * The tool does not support the JSON log of `--execution_log_json_file`.
 *
 * The report counts the actions by their source: the remote cache, the disk cache, or a local run.
 * It counts the test actions again in a separate section, and lists each of them. It ends with the largest blobs.
 * `--largest-blobs-count` limits the list of largest blobs. See [Summary].
 * `--html-report-path` also writes the report as an HTML page, e.g., for a TeamCity report tab. See [BazelExecutionHtmlReport].
 *
 * The size check lists the blobs that break the blob size limit. `--blob-size-limit-mb` sets that limit,
 * and `--blob-size-limit-extensions` restricts the check to a set of file extensions. A negative `--blob-size-limit-mb` turns the check off.
 * The tool throws when a blob breaks the limit, so a build step can guard against a blob that the remote cache must not hold.
 * Under TeamCity, the tool reports each of these blobs as a build problem instead.
 */
@ApiStatus.Internal
object BazelExecutionReport {
  internal const val TEST_MNEMONIC = "TestRunner"

  @JvmStatic
  fun main(args: Array<String>) {
    val options = BazelExecutionReportOptions.parse(args)
    val spawns = readSpawns(options.files) { it.remoteCacheable }
    val blobs = collectBlobs(spawns)
    val summary = Summary(spawns, options.largestBlobsCount, blobs)
    if (options.html != null) {
      BazelExecutionHtmlReport.write(options.html, summary, options.files)
    }
    val violations = violations(blobs, options)
    // The writer must not close `System.out`, so it only flushes.
    val out = System.out.bufferedWriter()
    try {
      out.reportSummary(summary)
      out.reportSizeCheck(violations, options)
    }
    finally {
      out.flush()
    }
  }

  /**
   * Reads the spawns of every log, in the order of [files]. An error names the log that caused it.
   */
  private fun readSpawns(files: List<Path>, filterOutputs: (Spawn) -> Boolean): List<Spawn> =
    files.flatMap { file ->
      try {
        BazelCompactExecutionLog.readSpawns(file, filterOutputs)
      }
      catch (e: IOException) {
        throw IOException("$file: ${e.message}", e)
      }
    }

  /**
   * Returns the blobs that break the blob size limit, largest first.
   *
   * The check runs only when `--blob-size-limit-mb` asks for it. A blob of exactly the limit stays inside it.
   * An empty extension set matches every blob. A blob holds several paths, and one matching path is enough.
   */
  private fun violations(blobs: List<Blob>, options: BazelExecutionReportOptions): List<Blob> {
    return if (options.checkBlobSizeViolations) {
      blobs.asSequence().filter { it.sizeBytes > options.maxSizeBytes }.filter { blob ->
        options.extensions.isEmpty() || blob.paths.any { path ->
          options.extensions.any {
            path.endsWith(".$it", ignoreCase = true)
          }
        }
      }.toList()
    }
    else {
      emptyList()
    }
  }

  /**
   * One remote cache entry. The digest hash identifies the content, so one hash gives one row.
   */
  internal class Blob(@JvmField val hash: String, @JvmField val sizeBytes: Long) {
    /** The distinct output paths, in sorted order. */
    @JvmField
    val paths: TreeSet<String> = TreeSet()

    /** The distinct target labels, in sorted order. */
    @JvmField
    val targets: TreeSet<String> = TreeSet()

    /** The distinct action kinds, in sorted order. */
    @JvmField
    val mnemonics: TreeSet<String> = TreeSet()
  }

  /**
   * Keeps the blobs the remote cache can hold.
   * The "bazel-out/" prefix is stripped from [Blob.paths].
   */
  private fun collectBlobs(spawns: List<Spawn>): List<Blob> {
    val blobs = HashMap<String, Blob>()
    for (spawn in spawns) {
      if (!spawn.remoteCacheable) {
        continue
      }
      for (output in spawn.outputs) {
        // Two outputs that share a hash share the content, so they share the size.
        val blob = blobs.computeIfAbsent(output.hash) {
          Blob(hash = it, sizeBytes = output.sizeBytes)
        }
        blob.paths.add(output.path.removePrefix("bazel-out/"))
        blob.targets.add(spawn.targetLabel)
        blob.mnemonics.add(spawn.mnemonic)
      }
    }
    return blobs.values.sortedWith(compareByDescending<Blob> { it.sizeBytes }.thenBy { it.hash })
  }

  /**
   * Where the actions came from, and the blobs that the actions run locally can add to the remote cache.
   *
   * One spawn counts as one action. A sharded or retried test gives one spawn for each shard and each attempt.
   * The log holds no entry for an action that starts no spawn, such as a symlink or a runfiles tree.
   *
   * A blob that two action kinds share counts in both rows of [uploadsByMnemonic]. [uploads] counts it once.
   * [largestBlobsCount] limits [largestBlobs] only. [uploadsByMnemonic] holds every action kind, and [tests] every test action.
   */
  internal class Summary(spawns: List<Spawn>, largestBlobsCount: Int, blobs: List<Blob>) {
    /** The number of actions for each source. */
    @JvmField
    val actions: Map<Source, Int> = spawns.groupingBy { it.source() }.eachCount()

    /** The number of test actions for each source. */
    @JvmField
    val testActions: Map<Source, Int>

    /** The test actions, sorted by their source and then by their target. */
    @JvmField
    val tests: List<Spawn>

    /** The size of each distinct blob that the actions run locally can upload, by hash. */
    @JvmField
    val uploads: Map<String, Long>

    /** The blobs of [uploads] for each action kind, largest first. */
    @JvmField
    val uploadsByMnemonic: List<Pair<String, Map<String, Long>>>

    /** The largest blobs that the remote cache can hold, from cache hits and local runs, up to the limit. */
    @JvmField
    val largestBlobs: List<Blob>

    /** The number of distinct blobs that the remote cache can hold, including the rows that the limit leaves out. */
    @JvmField
    val blobCount: Int

    /** The total size of the distinct blobs that the remote cache can hold. */
    @JvmField
    val blobSizeBytes: Long

    init {
      val testSpawns = spawns.filter { it.mnemonic == TEST_MNEMONIC }
      testActions = testSpawns.groupingBy { it.source() }.eachCount()
      tests = testSpawns.sortedWith(compareBy<Spawn> { it.source().ordinal }.thenBy { it.targetLabel })
      val uploadSpawns = spawns.filter { it.isUpload() }
      uploads = distinctBlobs(uploadSpawns)
      uploadsByMnemonic = uploadSpawns.groupBy { it.mnemonic }
        .map { (mnemonic, group) -> mnemonic to distinctBlobs(group) }
        .sortedWith(compareByDescending<Pair<String, Map<String, Long>>> { it.second.values.sum() }.thenBy { it.first })
      blobCount = blobs.size
      blobSizeBytes = blobs.sumOf { it.sizeBytes }
      largestBlobs = if (largestBlobsCount > 0) blobs.take(largestBlobsCount) else blobs
    }

    fun shownSources(): List<Source> {
      return Source.entries.filter {
        it != Source.OTHER_CACHE ||
        // A cache hit of an unknown cache appears only when its count is not zero.
        (actions[it] ?: 0) > 0 || (testActions[it] ?: 0) > 0
      }
    }

    /**
     * Describes what the test action adds to the remote cache: the size of its distinct output blobs, or why it adds nothing.
     *
     * Bazel uploads the outputs of a test action only after a successful local run of a remote-cacheable test.
     * A test with the `external` or `no-cache` tag is not remote-cacheable, and no test is under `--nocache_test_results`.
     */
    fun uploadText(test: Spawn): String = when {
      test.isUpload() -> formatSize(distinctBlobs(listOf(test)).values.sum())
      test.cacheHit -> "none, cache hit"
      !test.remoteCacheable -> "none, not cacheable"
      else -> "none, failed"
    }
  }

  /**
   * Returns the size of each distinct blob of the spawns, by hash.
   */
  internal fun distinctBlobs(spawns: List<Spawn>): Map<String, Long> {
    return spawns.asSequence()
      .flatMap { it.outputs }
      .associate { it.hash to it.sizeBytes }
  }

  /**
   * Writes the result of the size check, when the command line asks for it
   */
  private fun BufferedWriter.reportSizeCheck(violations: List<Blob>, options: BazelExecutionReportOptions) {
    if (!options.checkBlobSizeViolations) {
      return
    }
    reportViolations(violations, options)
    if (System.getenv("TEAMCITY_VERSION") != null) {
      reportBuildProblems(violations, options)
    }
    else {
      check(violations.none()) {
        "${violations.size} ${if (violations.size == 1) "blob breaks" else "blobs break"} the ${limitText(options)}"
      }
    }
  }

  /**
   * One output blob of a spawn. [BazelCompactExecutionLog] keeps only the outputs that have a digest.
   */
  internal class Output(@JvmField val hash: String, @JvmField val path: String, @JvmField val sizeBytes: Long)

  internal class Spawn(
    @JvmField val mnemonic: String,
    @JvmField val targetLabel: String,
    /** The name of the runner, for example `linux-sandbox`, `worker`, `remote cache hit`, or `disk cache hit`. */
    @JvmField val runner: String,
    @JvmField val exitCode: Int,
    @JvmField val cacheHit: Boolean,
    @JvmField val remoteCacheable: Boolean,
  ) {
    /**
     * The output blobs. [BazelCompactExecutionLog] sets them after its second pass, and only for the spawns that the caller selects.
     */
    @JvmField
    var outputs: List<Output> = emptyList()

    /**
     * Reports whether Bazel uploads the outputs of the spawn to the remote cache.
     *
     * Bazel uploads the result of a successful local run only.
     */
    fun isUpload(): Boolean = !cacheHit && remoteCacheable && exitCode == 0

    /**
     * Returns the source of the spawn result. Bazel names the runner of a cache hit `<cache> cache hit`.
     */
    fun source(): Source = when {
      runner == "remote cache hit" -> Source.REMOTE_CACHE
      runner == "disk cache hit" -> Source.DISK_CACHE
      cacheHit -> Source.OTHER_CACHE
      else -> Source.LOCAL
    }
  }

  internal enum class Source(@JvmField val text: String) {
    REMOTE_CACHE("remote cache hit"),
    DISK_CACHE("disk cache hit"),
    OTHER_CACHE("other cache hit"),
    LOCAL("run locally"),
  }
}
