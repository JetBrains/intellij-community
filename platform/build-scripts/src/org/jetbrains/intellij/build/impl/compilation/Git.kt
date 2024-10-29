// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.util.io.awaitExit
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class Git(private val dir: Path) {
  suspend fun log(commitCount: Int): List<String> {
    @Suppress("SpellCheckingInspection")
    return execute("git", "log", "-$commitCount", "--pretty=tformat:%H")
  }

  suspend fun formatLatestCommit(format: String): String {
    return execute("git", "log", "--pretty=format:$format", "-n", "1").joinToString("\n")
  }

  suspend fun listFilesUnderVersionControl(refSpec: String = "HEAD"): List<String> {
    return execute("git", "ls-tree", "-r", refSpec, "--name-only")
  }

  suspend fun currentCommitShortHash(): String {
    val lines = execute("git", "rev-parse", "--short", "HEAD")
    if (lines.size != 1) {
      throw IllegalStateException("Single line output is expected but got '$lines'")
    }
    val hash = lines.first().trim()
    if (hash.length != 13) {
      throw IllegalStateException("Short hash must be exactly 13 chars, but got '$hash'")
    }
    return hash
  }

  private suspend fun maybeExecute(vararg command: String): ExecutionResult {
    val process = ProcessBuilder(*command).directory(dir.toFile()).start()
    var output = process.inputStream.bufferedReader().use {
      it.lines().map { line -> line.trim() }.toList()
    }
    if (!process.waitFor(1, TimeUnit.MINUTES)) {
      process.destroyForcibly().awaitExit()
      throw IllegalStateException("Cannot execute $command: 1 minute timeout")
    }
    if (process.exitValue() != 0) {
      output = listOf(process.errorStream.bufferedReader().use { it.readText() }) + output
    }
    return ExecutionResult(process.exitValue(), output)
  }

  private suspend fun execute(vararg command: String): List<String> {
    val result = maybeExecute(*command)
    if (result.exitCode != 0) {
      throw IllegalStateException("git process failed with $result.exitCode:\n${result.output.joinToString("\n")}")
    }
    return result.output
  }
}

private data class ExecutionResult(@JvmField val exitCode: Int, @JvmField val output: List<String>)
