package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.SharedCache
import com.intellij.platform.buildScripts.concurrency.SharedTaskOwner
import com.intellij.platform.buildScripts.concurrency.taskScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class SharedTaskOwnerTest {
  @Test
  fun `cancelling a coroutine waits for the complete build lifetime`() {
    runBlocking {
      val entered = CompletableDeferred<Thread>()
      val disposed = AtomicInteger()
      val build = launch(Dispatchers.Default) {
        runInterruptible(Dispatchers.IO) {
          BuildLifetime().use { lifetime ->
            val cache = SharedCache<String, Int>(lifetime.sharedTasks) { disposed.addAndGet(it) }
            cache.getOrPut("value") {
              entered.complete(Thread.currentThread())
              try {
                CountDownLatch(1).await()
              }
              catch (_: InterruptedException) {
              }
              42
            }
          }
        }
      }
      withTimeout(5.seconds) {
        val worker = entered.await()
        build.cancelAndJoin()
        assertThat(worker.isAlive).isFalse()
        assertThat(disposed.get()).isEqualTo(42)
      }
    }
  }

  @Test
  fun `new loads retire terminated workers without retaining their threads`() {
    val owner = SharedTaskOwner("retirement")
    owner.use {
      val cache = SharedCache<Int, Thread>(owner)
      val workersField = SharedTaskOwner::class.java.getDeclaredField("workers").apply { isAccessible = true }
      repeat(2000) { key ->
        val worker = cache.getOrPut(key) { Thread.currentThread() }
        worker.join(5000)
        assertThat(worker.isAlive).isFalse()
        assertThat(workersField.get(owner) as Collection<*>).hasSize(1)
      }
    }
    for (fieldName in listOf("workers", "completedWorkers")) {
      val field = SharedTaskOwner::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
      assertThat(field.get(owner) as Collection<*>).isEmpty()
    }
  }

  @Test
  fun `closing the owner joins every concurrent loader before disposing values`() {
    repeat(10) {
      SharedTaskOwner("concurrent loaders").use { owner ->
        val loaderCount = 100
        val entered = CountDownLatch(loaderCount)
        val finished = AtomicInteger()
        val disposed = AtomicInteger()
        val workers = java.util.concurrent.ConcurrentLinkedQueue<Thread>()
        val cache = SharedCache<Int, Int>(owner) {
          assertThat(workers).allMatch { !it.isAlive }
          disposed.incrementAndGet()
        }
        taskScope {
          repeat(loaderCount) { key ->
            fork("waiter $key") {
              runCatching {
                cache.getOrPut(key) {
                  workers.add(Thread.currentThread())
                  entered.countDown()
                  try {
                    CountDownLatch(1).await()
                  }
                  catch (_: InterruptedException) {
                    finished.incrementAndGet()
                  }
                  key
                }
              }
            }
          }
          assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
          owner.close()
          join()
        }
        assertThat(finished.get()).isEqualTo(loaderCount)
        assertThat(disposed.get()).isEqualTo(loaderCount)
      }
    }
  }

  @Test
  fun `close waits for a late value and disposes it once`() {
    val owner = SharedTaskOwner("late value")
    val entered = CountDownLatch(1)
    val cancelled = CountDownLatch(1)
    val release = CountDownLatch(1)
    val disposed = AtomicInteger()
    val cache = SharedCache<String, Int>(owner) { disposed.addAndGet(it) }
    val waiter = Thread.ofVirtual().start {
      assertThatThrownBy {
        cache.getOrPut("value") {
          entered.countDown()
          try {
            CountDownLatch(1).await()
          }
          catch (_: InterruptedException) {
            cancelled.countDown(); awaitUninterruptibly(release)
          }
          42
        }
      }.isInstanceOf(IllegalStateException::class.java)
    }
    val closed = CompletableFuture<Unit>()
    val closer = Thread.ofVirtual().unstarted {
      try {
        owner.close(); closed.complete(Unit)
      }
      catch (failure: Throwable) {
        closed.completeExceptionally(failure)
      }
    }
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
      closer.start()
      assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(closed.isDone).isFalse()
      assertThat(disposed.get()).isZero()
    }
    finally {
      release.countDown()
      waiter.join(5000)
      closer.join(5000)
      owner.close()
    }
    assertThat(closed.get(5, TimeUnit.SECONDS)).isEqualTo(Unit)
    assertThat(disposed.get()).isEqualTo(42)
  }

  @Test
  fun `concurrent close waits for the same cleanup and reports the same failure`() {
    val owner = SharedTaskOwner("concurrent close")
    val cleaning = CountDownLatch(1)
    val release = CountDownLatch(1)
    val count = AtomicInteger()
    val failure = IllegalStateException("cleanup failed")
    owner.onClose {
      count.incrementAndGet()
      cleaning.countDown()
      check(release.await(5, TimeUnit.SECONDS))
      throw failure
    }
    val first = CompletableFuture<Throwable>()
    val second = CompletableFuture<Throwable>()
    val firstThread = Thread.ofVirtual().start { first.complete(runCatching { owner.close() }.exceptionOrNull()) }
    assertThat(cleaning.await(5, TimeUnit.SECONDS)).isTrue()
    val secondThread = Thread.ofVirtual().start { second.complete(runCatching { owner.close() }.exceptionOrNull()) }
    try {
      assertThat(second.isDone).isFalse()
    }
    finally {
      release.countDown(); firstThread.join(5000); secondThread.join(5000)
    }
    assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(failure)
    assertThat(second.get(5, TimeUnit.SECONDS)).isSameAs(failure)
    assertThat(count.get()).isEqualTo(1)
  }

  @Test
  fun `nested shared workers cannot close their owner`() {
    SharedTaskOwner("nested worker").use { owner ->
      val cache = SharedCache<String, Int>(owner)
      assertThatThrownBy {
        cache.getOrPut("value") {
          taskScope {
            fork("nested") { owner.close() }
            join { 42 }
          }
        }
      }.hasRootCauseMessage("A shared worker cannot close its own owner 'nested worker'")
    }
  }

  @Test
  fun `a closed owner rejects loads and immediately closes late resources`() {
    val owner = SharedTaskOwner("closed")
    val cache = SharedCache<String, Int>(owner)
    owner.close()
    owner.close()
    val closed = AtomicInteger()
    owner.onClose { closed.incrementAndGet() }
    assertThat(closed.get()).isEqualTo(1)
    assertThatThrownBy { cache.getOrPut("value") { 42 } }.isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun `an interrupted cleanup failure does not become an endless wait`() {
    val owner = SharedTaskOwner("interrupted cleanup")
    val failure = InterruptedException("resource failed")
    owner.onClose { throw failure }
    repeat(2) { assertThatThrownBy { owner.close() }.isSameAs(failure) }
  }

  @Test
  fun `a loader cannot close its cache while another thread closes it`() {
    SharedTaskOwner("cache shutdown").use { owner ->
      val cache = SharedCache<String, Int>(owner)
      val entered = CountDownLatch(1)
      val failure = CompletableFuture<Throwable?>()
      val waiter = Thread.ofVirtual().start {
        runCatching {
          cache.getOrPut("value") {
            entered.countDown()
            try {
              CountDownLatch(1).await()
            }
            finally {
              failure.complete(runCatching { cache.close() }.exceptionOrNull())
            }
            42
          }
        }
      }
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        cache.close()
        assertThat(failure.get(5, TimeUnit.SECONDS))
          .isInstanceOf(IllegalStateException::class.java)
          .hasMessage("Recursive await of 'closing a shared cache' detected")
      }
      finally {
        waiter.interrupt()
        waiter.join(5000)
      }
    }
  }

  @Test
  fun `closing one cache cancels its nested tasks without cancelling other caches`() {
    SharedTaskOwner("caches").use { owner ->
      val first = SharedCache<String, Int>(owner)
      val second = SharedCache<String, Int>(owner)
      val entered = CountDownLatch(1)
      val cleaned = CountDownLatch(1)
      val waiter = Thread.ofVirtual().start {
        runCatching {
          first.getOrPut("value") {
            taskScope {
              fork("nested") {
                entered.countDown()
                try {
                  CountDownLatch(1).await()
                }
                finally {
                  cleaned.countDown()
                }
              }
              join { 1 }
            }
          }
        }
      }
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        first.close()
        assertThat(cleaned.count).isZero()
        assertThat(second.getOrPut("value") { 42 }).isEqualTo(42)
      }
      finally {
        first.close(); waiter.join(5000)
      }
    }
  }

  private fun awaitUninterruptibly(latch: CountDownLatch) {
    while (true) {
      try {
        check(latch.await(5, TimeUnit.SECONDS)); return
      }
      catch (_: InterruptedException) {
      }
    }
  }
}
