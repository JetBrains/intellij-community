// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import groovy.transform.CompileStatic
import groovy.transform.Immutable

import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

@CompileStatic
final class Git {
  private static final long PROCESS_TIMEOUT = 10_000

  private final File dir

  Git(String dir) {
    this.dir = new File(dir)
  }

  List<String> log(int commitsCount) {
    return execute("git log -$commitsCount --pretty=tformat:%H")
  }

  List<String> status() {
    return execute('git status --short --untracked-files=no --ignored=no')
  }

  String lineBreaksConfig() {
    def lines = maybeExecute("git config core.autocrlf").output.findAll { !it.isBlank() }
    if (lines.isEmpty()) {
      return ""
    }
    if (lines.size() != 1) {
      throw new IllegalStateException("Single line output is expected but got '$lines'")
    }
    return lines[0]
  }

  private ExecutionResult maybeExecute(String command) {
    def process = command.execute((List)null, dir)
    List<String> output = new BufferedReader(new InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).withCloseable {
      it.lines().map { it.trim() }.collect(Collectors.toList())
    }
    process.waitForOrKill(PROCESS_TIMEOUT)
    if (process.exitValue() != 0) {
      output = [process.errorStream.text] + output
    }
    return new ExecutionResult(exitCode: process.exitValue(), output: output)
  }

  private List<String> execute(String command) {
    ExecutionResult result = maybeExecute(command)
    if (result.exitCode != 0) {
      throw new IllegalStateException("git process failed with $result.exitCode:\n${result.output.join('\n')}")
    }
    return result.output
  }

  @Immutable
  private class ExecutionResult {
    int exitCode
    List<String> output
  }
}
