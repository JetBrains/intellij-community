// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.concurrency.Joiner
import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class PackagingTasksTest {
  @Test
  fun `all tasks register on the owner and run on demand on virtual threads`() {
    taskScope {
      val tasks = PackagingTasks(this, PackagingSuiteHangDiagnostics())
      val ran = AtomicBoolean()
      val first = tasks.task("first") {
        assertThat(Thread.currentThread().isVirtual).isTrue()
        ran.set(true)
        21
      }
      val second = tasks.task("second") { first.await() * 2 }
      assertThat(ran.get()).isFalse()
      assertThat(second.await(5.seconds)).isEqualTo(42)
      join()
    }
  }

  @Test
  fun `await all lets independent tasks succeed after a failure`() {
    taskScope(joiner = Joiner.awaitAll()) {
      val tasks = PackagingTasks(this, PackagingSuiteHangDiagnostics())
      val failed = tasks.task("failed") { error("failure") }
      val success = tasks.task("success") { 42 }
      assertThatThrownBy { failed.await(5.seconds) }.hasMessage("failure")
      assertThat(success.await(5.seconds)).isEqualTo(42)
      join()
    }
  }
}
