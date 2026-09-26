package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.taskScope
import com.intellij.platform.buildScripts.concurrency.withLockInterruptibly
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

@Timeout(20)
class InterruptibleLockTest {
  @Test
  fun `an interrupted waiter exits before the lock is released`() {
    val lock = ReentrantLock()
    val entered = CountDownLatch(1)
    val result = CompletableFuture<Throwable?>()
    lock.withLockInterruptibly {
      val waiter = Thread.ofVirtual().start {
        entered.countDown()
        result.complete(runCatching { lock.withLockInterruptibly { error("The action must not run") } }.exceptionOrNull())
      }
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        waiter.interrupt()
        assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOf(InterruptedException::class.java)
      }
      finally {
        waiter.interrupt()
        waiter.join(5000)
      }
      assertThat(waiter.isAlive).isFalse()
    }
  }

  @Test
  fun `an interrupted caller does not enter an available lock`() {
    val lock = ReentrantLock()
    try {
      Thread.currentThread().interrupt()
      assertThatThrownBy { lock.withLockInterruptibly { error("The action must not run") } }
        .isInstanceOf(InterruptedException::class.java)
    }
    finally {
      Thread.interrupted()
    }
    assertThat(lock.isLocked).isFalse()
  }

  @Test
  fun `the lock is reentrant and a failed action releases it`() {
    val lock = ReentrantLock()
    val failure = IllegalStateException("The action failed")
    assertThatThrownBy {
      lock.withLockInterruptibly {
        assertThat(lock.withLockInterruptibly { lock.holdCount }).isEqualTo(2)
        throw failure
      }
    }.isSameAs(failure)
    assertThat(lock.isLocked).isFalse()
  }

  @Test
  fun `a cancelled scope owner does not enter the lock`() {
    val lock = ReentrantLock()
    val failure = IllegalStateException("A child failed")
    assertThatThrownBy {
      taskScope {
        val child = fork("failure") { throw failure }
        assertThatThrownBy { child.await() }.hasCause(failure)
        assertThatThrownBy { lock.withLockInterruptibly { error("The action must not run") } }.hasCause(failure)
        assertThat(lock.isLocked).isFalse()
        join()
      }
    }.hasCause(failure)
  }

  @Test
  fun `task cancellation survives a cleared interrupt and releases the lock`() {
    val lock = ReentrantLock()
    val entered = CountDownLatch(1)
    val result = CompletableFuture<Throwable?>()
    val failure = IllegalStateException("A sibling failed")
    assertThatThrownBy {
      taskScope {
        fork("waiter") {
          entered.countDown()
          try {
            CountDownLatch(1).await()
          }
          catch (_: InterruptedException) {
            result.complete(runCatching { lock.withLockInterruptibly { error("The action must not run") } }.exceptionOrNull())
          }
        }
        fork("failure") {
          check(entered.await(5, TimeUnit.SECONDS))
          throw failure
        }
        join()
      }
    }.hasCause(failure)
    assertThat(result.get(5, TimeUnit.SECONDS)).hasCause(failure)
    assertThat(lock.isLocked).isFalse()
  }
}
