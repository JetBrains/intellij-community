// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.junit.runner.JUnitCore

@Suppress("unused")
fun runTest(test: Class<*>) {
  val result = if (isUnderTeamCity()) {
    JUnitCore.runClasses(test)
  }
  else muteStdOut {
    JUnitCore.runClasses(test)
  }
  println("$test, tests: ${result.runCount}, failed: ${result.failureCount}")
  if (result.failureCount > 0) {
    System.err.println(result.failures.joinToString(separator = "\n", prefix = "\t") {
      "${it.testHeader}: ${it.trace}"
    })
  }
}