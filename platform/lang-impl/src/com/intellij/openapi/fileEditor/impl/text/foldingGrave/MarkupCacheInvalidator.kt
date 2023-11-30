// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
@Service(Service.Level.APP)
internal class MarkupCacheInvalidator {
  private val cleaned = AtomicBoolean(false)
  private val mutex = Mutex()

  companion object {
    private val logger: Logger = Logger.getInstance(MarkupCacheInvalidator::class.java)
    private val markerFile: Path
      get() = PathManager.getSystemDir().resolve("persistent-markup").resolve(".invalidated")
    private val caches: List<Path>
      get() = listOf(
        PathManager.getSystemDir().resolve("persistent-folding"),
        PathManager.getSystemDir().resolve("persistent-markup"),
      )
  }

  @RequiresBackgroundThread
  suspend fun cleanCacheIfNeeded() {
    if (cleaned.get()) {
      return
    }
    mutex.withLock {
      if (Files.exists(markerFile)) {
        cleanCaches()
      }
      cleaned.set(true)
    }
  }

  private fun cleanCaches() {
    logger.info("invalidating persistent markup")
    for (cacheDir in caches) {
      try {
        NioFiles.deleteRecursively(cacheDir)
      } catch (e: IOException) {
        logger.error("invalidation error ${cacheDir.fileName}", e)
      }
    }
  }

  override fun toString() = "MarkupCacheInvalidator(cleaned=${cleaned.get()})"

  class InvalidationRequest : CachesInvalidator() {
    override fun invalidateCaches() {
      markInvalidated()
    }

    private fun markInvalidated() {
      try {
        Files.createDirectories(markerFile.parent)
        Files.write(markerFile, ByteArray(0), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
      } catch (e: IOException) {
        logger.error("error while creating invalidation marker file", e)
      }
    }
  }
}
