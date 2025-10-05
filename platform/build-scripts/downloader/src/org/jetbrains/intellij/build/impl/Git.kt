// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

@ApiStatus.Internal
class Git(private val dir: Path) {
  fun log(commitCount: Int): List<String> {
    @Suppress("SpellCheckingInspection")
    return execute("git", "log", "-$commitCount", "--pretty=tformat:%H")
  }

  fun formatLatestCommit(format: String): String {
    return execute("git", "log", "--pretty=format:$format", "-n", "1").joinToString("\n")
  }

  fun listTree(refSpec: String = "HEAD"): List<String> {
    return executeWithNullSeparatedOutput("git", "ls-tree", "-z", "-r", refSpec, "--name-only")
  }

  fun listStagingFiles(): List<String> {
    return executeWithNullSeparatedOutput("git", "ls-files", "-z")
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
    val process = ProcessBuilder(*command).directory(dir.toFile()).start()
    var output = process.inputStream.bufferedReader().use {
      it.lines().map { line -> line.trim() }.toList()
    }
    if (!process.waitFor(1, TimeUnit.MINUTES)) {
      process.destroyForcibly().waitFor()
      throw IllegalStateException("Cannot execute $command: 1 minute timeout")
    }
    if (process.exitValue() != 0) {
      output = listOf(process.errorStream.bufferedReader().use { it.readText() }) + output
    }
    return ExecutionResult(process.exitValue(), output)
  }

  private fun executeWithNullSeparatedOutput(vararg command: String): List<String> {
    val process = ProcessBuilder(*command)
      .redirectError(Redirect.INHERIT)
      .directory(dir.toFile())
      .start()
    process.outputStream.close()

    val memoryStream = ByteArrayOutputStream()
    process.inputStream.copyTo(memoryStream)

    if (!process.waitFor(5, TimeUnit.MINUTES)) {
      process.destroyForcibly().waitFor()
      throw IllegalStateException("Cannot execute ${command.toList()}: 5 minutes timeout")
    }

    val exitCode = process.exitValue()
    if (exitCode != 0) {
      throw IllegalStateException("Cannot execute ${command.toList()}: exit code $exitCode")
    }

    return memoryStream.toByteArray().decodeToString().split('\u0000').filter { it.isNotEmpty() }
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
