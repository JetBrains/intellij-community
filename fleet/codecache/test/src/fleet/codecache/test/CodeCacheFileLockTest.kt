package fleet.codecache.test

import fleet.bundles.Coordinates
import fleet.bundles.ResolutionException
import fleet.codecache.CodeCache
import fleet.codecache.CodeCachePath
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.fail

class CodeCacheFileLockTest {
  @Test
  fun `consequential requests do not hang`() {
    val testTimeout = 1000L
    codeCacheTest(testTimeout, testTimeout / 2) { cc, cacheDir ->
      val filename = "file.txt"
      val mockCoord = Coordinates.Local(filename)

      cc.withFileLock(cacheDir, filename, mockCoord) {}
      cc.withFileLock(cacheDir, filename, mockCoord) {}
    }
  }

  @Test
  fun `simultaneous requests stress test`() {
    val testTimeout = 10000L
    codeCacheTest(testTimeout, testTimeout) { cc, cacheDir ->
      val acquired = AtomicBoolean(false)

      repeat(50) {
        val filename = "file.txt"
        val mockCoord = Coordinates.Local(filename)

        launch {
          cc.withFileLock(cacheDir, filename, mockCoord) {
            try {
              if (acquired.getAndSet(true)) fail("More than one client")
              delay(10)
            }
            finally {
              acquired.set(false)
            }
          }
        }
      }
    }
  }

  @Test
  fun `forgotten lock file ends up with exception`() {
    val testTimeout = 3000L
    codeCacheTest(testTimeout, testTimeout / 3) { cc, cacheDir ->
      val filename = "file.txt"
      val mockCoord = Coordinates.Local(filename)

      val semaphore = Semaphore(1, 1)
      // lock the file "forever"
      val lockJob = launch {
        cc.withFileLock(cacheDir, filename, mockCoord) {
          semaphore.release()
          delay(testTimeout * 2)
        }
      }

      semaphore.acquire()
      //file here is already locked
      try {
        cc.withFileLock(cacheDir, filename, mockCoord) {
          fail("This code should not be reached, the file must be still locked")
        }
      }
      catch (e: ResolutionException) {
        //this is ok, cancel the "forever" job, otherwise it will be locked
        lockJob.cancel()
      }
    }
  }

  @Test
  fun `exception does not break locking mechanism`() {
    val testTimeout = 1000L
    codeCacheTest(testTimeout, testTimeout / 2) { cc, cacheDir ->
      val filename = "file.txt"
      val mockCoord = Coordinates.Local(filename)

      try {
        cc.withFileLock(cacheDir, filename, mockCoord) {
          throw RuntimeException()
        }
      }
      catch (_: RuntimeException) {
      }

      cc.withFileLock(cacheDir, filename, mockCoord) {}
    }
  }

  private fun codeCacheTest(testTimeout: Long, maxLockWait: Long, body: suspend CoroutineScope.(CodeCache, Path) -> Unit) {
    runBlocking {
      withTimeout(testTimeout) {
        val cacheDir = Files.createTempDirectory("codeCacheTest")
        val codeCachePaths = listOf(CodeCachePath(cacheDir, writable = true))
        val cc = CodeCache({ HttpClient { } }, codeCachePaths, maxLockWaitTime = maxLockWait)
        body(cc, cacheDir)
      }
    }
  }
}
