package org.jetbrains.intellij.build.impl

import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import com.intellij.platform.buildScripts.concurrency.TaskSignal
import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.ResolvedDownload
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

@Timeout(10)
class BundledMavenDownloaderLifetimeTest {
  private val libraries = listOf("test:first:1", "test:second:2")

  @Test
  fun `downloads use virtual threads and retain the library order`() {
    val secondFinished = CountDownLatch(1)
    val files = BundledMavenDownloader.resolveMavenLibs(libraries) { url ->
      assertThat(Thread.currentThread().isVirtual).isTrue()
      val fileName = url.substringAfterLast('/')
      if (fileName == "first-1.jar") {
        secondFinished.await()
      }
      else {
        secondFinished.countDown()
      }
      ResolvedDownload(Path.of(fileName), "digest-$fileName")
    }

    assertThat(files).containsExactly(
      BundledMavenDownloader.MavenLibraryFile("first-1.jar", Path.of("first-1.jar"), "digest-first-1.jar"),
      BundledMavenDownloader.MavenLibraryFile("second-2.jar", Path.of("second-2.jar"), "digest-second-2.jar"),
    )
  }

  @Test
  fun `a later failure interrupts and joins an earlier download`() {
    val firstStarted = CountDownLatch(1)
    val firstWorker = AtomicReference<Thread>()
    val failure = IllegalStateException("download failed")

    val actual = assertThrows<TaskFailedException> {
      BundledMavenDownloader.resolveMavenLibs(libraries) { url ->
        if (url.endsWith("first-1.jar")) {
          firstWorker.set(Thread.currentThread())
          firstStarted.countDown()
          CountDownLatch(1).await()
          error("The first download must be interrupted")
        }
        firstStarted.await()
        throw failure
      }
    }

    assertThat(actual.cause).isSameAs(failure)
    assertThat(firstWorker.get().isAlive).isFalse()
  }

  @Test
  fun `caller interruption stops every download before returning`() {
    val caller = TaskSignal<Thread>()
    val workersStarted = CountDownLatch(libraries.size)
    val workers = ConcurrentLinkedQueue<Thread>()

    taskScope {
      val interrupted = fork("resolve Maven libraries") {
        caller.complete(Thread.currentThread())
        assertThrows<InterruptedException> {
          BundledMavenDownloader.resolveMavenLibs(libraries) {
            workers.add(Thread.currentThread())
            workersStarted.countDown()
            CountDownLatch(1).await()
            error("The download must be interrupted")
          }
        }
        Thread.currentThread().isInterrupted
      }
      workersStarted.await()
      caller.await().interrupt()
      join()
      assertThat(interrupted.get()).isTrue()
    }

    assertThat(workers).hasSize(libraries.size).allSatisfy { worker -> assertThat(worker.isAlive).isFalse() }
  }
}
