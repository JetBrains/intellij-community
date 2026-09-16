package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.awaitUninterruptibly
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Timeout(20)
class UninterruptibleFutureTest {
  @Test
  fun `waiting preserves an existing interrupt`() {
    try {
      Thread.currentThread().interrupt()
      assertThat(CompletableFuture.completedFuture(42).awaitUninterruptibly()).isEqualTo(42)
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    }
    finally {
      Thread.interrupted()
    }
  }

  @Test
  fun `interrupting a wait does not skip completion`() {
    val completion = CompletableFuture<Int>()
    val entered = CountDownLatch(1)
    val result = CompletableFuture<Pair<Int, Boolean>>()
    val waiter = Thread.ofVirtual().start {
      entered.countDown()
      val value = completion.awaitUninterruptibly()
      result.complete(value to Thread.currentThread().isInterrupted)
    }
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
      waiter.interrupt()
      assertThat(result.isDone).isFalse()
      completion.complete(42)
      assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(42 to true)
    }
    finally {
      completion.complete(42)
      waiter.join(5000)
    }
    assertThat(waiter.isAlive).isFalse()
  }

  @Test
  fun `an interrupted task failure is rethrown without retrying`() {
    val failure = InterruptedException("The task failed")
    try {
      Thread.currentThread().interrupt()
      assertThatThrownBy { CompletableFuture.failedFuture<Unit>(failure).awaitUninterruptibly() }.isSameAs(failure)
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    }
    finally {
      Thread.interrupted()
    }
  }
}
