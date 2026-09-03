// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
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
  private val zipFileLoader: suspend (Path) -> ZipFile? = { loadZipFile(it) },
) {
  private val cache: AsyncCache<Path, ZipFile?>? = scope?.let {
    AsyncCache<Path, ZipFile?>().also { cache ->
      scope.coroutineContext.job.invokeOnCompletion {
        cache.close { zipFile -> zipFile?.close() }
      }
    }
  }

  suspend fun getData(file: Path, entryPath: String): ByteArray? {
    try {
      // `AsyncCache` runs the load on a virtual thread. Without a cache the load runs inline, and a build caller is
      // on a virtual thread too.
      return withTimeout(cacheReadTimeout) {
        if (cache == null) {
          zipFileLoader(file)?.use { it.getData(entryPath) }
        }
        else {
          cache.getOrPut(file) { zipFileLoader(file) }?.getData(entryPath)
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      // The dump goes to stderr, not into the message. The build server truncates a message of this size,
      // and the truncation deletes the stack trace under it.
      val dump = dumpCoroutines()
      if (dump != null) {
        System.err.println("Coroutine dump for the timed out read of '$entryPath' from '$file':\n$dump")
      }
      throw IllegalStateException(
        "Timed out after $cacheReadTimeout reading '$entryPath' from archived module output '$file'." +
        (if (dump == null) "" else " A coroutine dump is on stderr."),
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
