// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import com.sun.management.HotSpotDiagnosticMXBean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Pool of opened [ImmutableZipFile] instances for efficient O(1) lookups.
 * Uses [AsyncCache] to deduplicate concurrent requests for the same file.
 *
 * If [scope] is provided, caching is enabled and all cached files are closed when the scope is canceled/completed.
 * If [scope] is null, no caching is performed - each call loads the file directly.
 */
@ApiStatus.Internal
class ModuleOutputZipFilePool(
  scope: CoroutineScope?,
  private val cacheReadTimeout: Duration = 2.minutes,
  private val zipFileLoader: (Path) -> ZipFile? = { loadZipFile(it) },
) {
  private val cache: AsyncCache<Path, ZipFile?>? = scope?.let {
    AsyncCache<Path, ZipFile?>().also { cache ->
      scope.coroutineContext.job.invokeOnCompletion {
        cache.close { zipFile -> zipFile?.close() }
      }
    }
  }

  fun getData(file: Path, entryPath: String): ByteArray? {
    try {
      // `AsyncCache` runs the load on a virtual thread. Without a cache the load runs inline, and a build caller is
      // on a virtual thread too.
      if (cache == null) {
        return zipFileLoader(file)?.use { it.getData(entryPath) }
      }
      return cache.getOrPut(file, timeout = cacheReadTimeout) { zipFileLoader(file) }?.getData(entryPath)
    }
    catch (e: TimeoutException) {
      // The dump goes to stderr, not into the message. The build server truncates a message of this size,
      // and the truncation deletes the stack trace under it. Both the reader and the load are virtual threads,
      // so a coroutine dump would show neither of them.
      val dump = dumpVirtualThreads()
      if (dump != null) {
        System.err.println("Thread dump for the timed out read of '$entryPath' from '$file':\n$dump")
      }
      throw IllegalStateException(
        "Timed out after $cacheReadTimeout reading '$entryPath' from archived module output '$file'." +
        (if (dump == null) "" else " A thread dump is on stderr."),
        e,
      )
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      throw IllegalStateException("Cannot read '$entryPath' from archived module output '$file'", e)
    }
  }

  private companion object {
    /** `ThreadMXBean` does not list a virtual thread, so the dump comes from the HotSpot bean. */
    fun dumpVirtualThreads(): String? {
      val file = Files.createTempFile("build-thread-dump", ".txt")
      try {
        // the bean writes the file itself and fails when it exists already
        file.deleteIfExists()
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
          .dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN)
        return file.readText()
      }
      catch (_: Exception) {
        return null
      }
      finally {
        try {
          file.deleteIfExists()
        }
        catch (_: Exception) {
        }
      }
    }

    fun loadZipFile(file: Path): ZipFile? {
      try {
        return ImmutableZipFile.load(file)
      }
      catch (e: IOException) {
        if (Files.notExists(file)) {
          return null
        }
        throw e
      }
    }
  }
}
