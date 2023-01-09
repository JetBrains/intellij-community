// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class Git(val dir: Path) {
  fun log(commitsCount: Int): List<String> {
    return execute("git", "log", "-$commitsCount", "--pretty=tformat:%H")
  }

  fun formatLatestCommit(format: String): String {
    val lines = execute("git", "log", "--pretty=format:$format", "-n", "1")
    return StringUtil.join(lines, "\n")
  }

  fun listFilesUnderVersionControl(refSpec: String = "HEAD"): List<String> {
    return execute("git", "ls-tree", "-r", refSpec, "--name-only")
  }

  fun currentCommitShortHash(): String {
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

  fun lineBreaksConfig(): String {
    val lines = maybeExecute("git", "config", "core.autocrlf").output.filter { !it.isBlank() }
    if (lines.isEmpty()) {
      return ""
    }
    if (lines.size != 1) {
      throw IllegalStateException("Single line output is expected but got '$lines'")
    }
    return lines.first()
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

  private fun execute(vararg command: String): List<String> {
    val result = maybeExecute(*command)
    if (result.exitCode != 0) {
      throw IllegalStateException("git process failed with $result.exitCode:\n${result.output.joinToString("\n")}")
    }
    return result.output
  }

  private data class ExecutionResult(val exitCode: Int, val output: List<String>)
}
