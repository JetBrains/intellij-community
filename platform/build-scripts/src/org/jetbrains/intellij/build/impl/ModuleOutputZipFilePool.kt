// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Pool of opened [ImmutableZipFile] instances for efficient O(1) lookups.
 * Uses [AsyncCache] to deduplicate concurrent requests for the same file.
 *
 * If [scope] is provided, caching is enabled and all cached files are closed when the scope is canceled/completed.
 * If [scope] is null, no caching is performed - each call loads the file directly.
 */
internal class ModuleOutputZipFilePool(scope: CoroutineScope?) {
  private val cache: AsyncCache<Path, ZipFile?>? = scope?.let {
    AsyncCache<Path, ZipFile?>(it).also { cache ->
      scope.coroutineContext.job.invokeOnCompletion {
        cache.close { zipFile -> zipFile?.close() }
      }
    }
  }

  suspend fun getData(file: Path, entryPath: String): ByteArray? {
    if (cache == null) {
      return loadZipFile(file)?.use { it.getData(entryPath) }
    }
    else {
      return cache.getOrPut(file) { loadZipFile(file) }?.getData(entryPath)
    }
  }

  private suspend fun loadZipFile(file: Path): ZipFile? {
    return withContext(Dispatchers.IO) {
      try {
        ImmutableZipFile.load(file)
      }
      catch (e: IOException) {
        if (Files.notExists(file)) {
          return@withContext null
        }
        throw e
      }
    }
  }
}
