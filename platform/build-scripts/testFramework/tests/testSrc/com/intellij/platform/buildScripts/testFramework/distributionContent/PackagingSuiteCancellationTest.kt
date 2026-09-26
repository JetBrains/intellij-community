package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.concurrency.TaskSignal
import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class PackagingSuiteCancellationTest {
  @Test
  fun `closing an unused fixture cancels its workers without starting a build`(@TempDir home: Path) {
    val fixture = PackagingSuiteFixture.create(PackagingSuiteSpec(
      name = "unused fixture",
      homePath = home,
      targets = listOf(PackagingTargetSpec(
        id = "not started",
        createProductProperties = { error("The build must not start") },
        contentYamlPath = "unused.yaml",
      )),
    ))
    try {
      Thread.currentThread().interrupt()
      fixture.close()
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    }
    finally {
      Thread.interrupted()
      fixture.close()
    }
    val build = fixture.createBuildTests().single()
    assertThatThrownBy { build.executable.execute() }.isInstanceOf(InterruptedException::class.java)
  }

  @Test
  fun `fixture cancellation interrupts blocking work and waits for cleanup`() {
    val started = CountDownLatch(1)
    val cleaned = AtomicBoolean()
    val handle = CompletableFuture<PackagingTaskHandle<Unit>>()
    val owner = Thread.ofVirtual().start {
      try {
        taskScope {
          val tasks = PackagingTasks(this, PackagingSuiteHangDiagnostics())
          handle.complete(tasks.task("build", startImmediately = true) {
            started.countDown()
            try {
              CountDownLatch(1).await()
            }
            finally {
              cleaned.set(true)
            }
          })
          join()
        }
      }
      catch (_: InterruptedException) {
      }
    }
    try {
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
      owner.interrupt()
      awaitThreadTermination(owner)
      assertThat(cleaned.get()).isTrue()
      assertThatThrownBy { handle.get(5, TimeUnit.SECONDS).await(5.seconds) }.isInstanceOf(InterruptedException::class.java)
    }
    finally {
      owner.interrupt(); awaitThreadTermination(owner)
    }
  }

  @Test
  fun `an interrupted waiter leaves the shared result unchanged`() {
    val result = TaskSignal<Int>("shared result")
    val started = CountDownLatch(1)
    val interrupted = CompletableFuture<Boolean>()
    val waiter = Thread.ofVirtual().start {
      started.countDown()
      try {
        result.await(); interrupted.complete(false)
      }
      catch (_: InterruptedException) {
        interrupted.complete(true)
      }
    }
    try {
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
      waiter.interrupt()
      assertThat(interrupted.get(5, TimeUnit.SECONDS)).isTrue()
      assertThat(result.isDone).isFalse()
      result.complete(42)
      assertThat(result.await()).isEqualTo(42)
    }
    finally {
      waiter.interrupt(); awaitThreadTermination(waiter)
    }
  }

  @Test
  fun `cancellation releases handles whose work never starts`() {
    lateinit var handle: PackagingTaskHandle<Unit>
    assertThatThrownBy {
      taskScope {
        handle = PackagingTasks(this, PackagingSuiteHangDiagnostics()).task("not started") { error("must not run") }
      }
    }.hasMessageContaining("without a join")
    assertThat(handle.isDone).isTrue()
    assertThatThrownBy { handle.await(5.seconds) }.isInstanceOf(java.util.concurrent.CancellationException::class.java)
  }
}
