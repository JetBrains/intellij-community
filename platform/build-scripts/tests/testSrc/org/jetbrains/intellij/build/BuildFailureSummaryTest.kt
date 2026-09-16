// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BuildFailureSummaryTest {
  @Test
  fun `summary prints each message once and keeps the chain order`() {
    val root = RuntimeException("Process './build.sh' (pid=1) finished with exitCode 2")
    val step = BuildScriptsLoggedError("'mac_dmg' build step failed", root)
    val task = TaskFailedException(step)
    val outer = BuildScriptsLoggedError("'os_specific_distributions' build step failed", task)

    val summary = buildFailureSummary(outer)

    assertThat(summary).contains("BUILD FAILED")
    val bullets = summary.lines().filter { it.startsWith("* ") }
    assertThat(bullets).containsExactly(
      "* 'os_specific_distributions' build step failed",
      "* 'mac_dmg' build step failed",
      "* RuntimeException: Process './build.sh' (pid=1) finished with exitCode 2",
    )
  }

  @Test
  fun `summary uses the class name when a message is absent`() {
    val summary = buildFailureSummary(IllegalStateException())
    assertThat(summary).contains("* java.lang.IllegalStateException")
  }

  @Test
  fun `summary truncates a long message`() {
    val summary = buildFailureSummary(RuntimeException("x".repeat(5000)))
    assertThat(summary).contains("[truncated, see the stack trace above]")
    assertThat(summary.length).isLessThan(2000)
  }
}
