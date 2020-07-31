// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

@CompileStatic
class Git {
  private static final long PROCESS_TIMEOUT = 10_000

  private final File dir

  Git(String dir) {
    this.dir = new File(dir)
  }

  List<String> log(int commitsCount) {
    return execute("git log -$commitsCount --pretty=tformat:%H")
  }

  String currentBranch(boolean allowDetachedHead) {
    try {
      // Command will fail in case of detached HEAD (e.g. during Safe Push)
      return execute('git symbolic-ref --short HEAD').first()
    } catch(IllegalStateException e) {
      if (allowDetachedHead) {
        return null
      }

      throw e
    }
  }

  private List<String> execute(String command) {
    def process = command.execute((List)null, dir)
    def output = new BufferedReader(new InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).withCloseable {
      it.lines().map { it.trim() }.collect(Collectors.toList())
    }
    process.waitForOrKill(PROCESS_TIMEOUT)
    if (process.exitValue() != 0) {
      throw new IllegalStateException("git process failed:\n$process.errorStream.text\n$output")
    }
    return output
  }
}
