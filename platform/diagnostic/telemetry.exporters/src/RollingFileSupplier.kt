// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.openapi.util.io.FileSetLimiter
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Supplier

@ApiStatus.Internal
class RollingFileSupplier(
  private val basePath: Path,
  private val initialDataToWrite: List<String> = listOf(),
  val maxFilesToKeep: Int = SystemProperties.getIntProperty("idea.diagnostic.opentelemetry.metrics.max-files-to-keep", 14)
) : Supplier<Path> {
  private val maxSizeBeforeRoll: Long = 10 * 1024 * 1024
  private var currentPath: Path? = null

  override fun get(): Path = get(forceToGetNewPath = false)

  fun get(forceToGetNewPath: Boolean = false): Path {
    val generateNewPath = currentPath?.let {
      try {
        Files.exists(it) && Files.size(it) > maxSizeBeforeRoll
      }
      catch (e: NoSuchFileException) {
        false
      }
    } ?: true

    if (forceToGetNewPath || generateNewPath) {
      currentPath = generateFileForMetrics(basePath)
      Files.write(currentPath, initialDataToWrite, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }

    return currentPath!!
  }

  internal fun init(initialDataToWrite: List<String> = listOf()): Path {
    val pathToWrite = get()
    if (!Files.exists(pathToWrite)) {
      val parentDir = pathToWrite.parent
      if (!Files.isDirectory(parentDir)) {
        //RC: createDirectories() _does_ throw FileAlreadyExistsException if path is a _symlink_ to a directory, not a directory
        // itself (JDK-8130464). Check !isDirectory() above should work around that case.
        Files.createDirectories(parentDir)
      }
    }
    if (!Files.exists(pathToWrite) || Files.size(pathToWrite) == 0L) {
      Files.write(pathToWrite, initialDataToWrite, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }

    return pathToWrite
  }

  /**
   * Creates new file for reporting OTel metrics.
   *
   * Generated file name is derived from metricsReportingBasePath, but with date/time/sequential-number additions,
   * i.e. '/var/logs/open-telemetry-metrics.csv' (base) -> '/var/logs/open-telemetry-metrics-2023-02-04-14-15-43.1.csv'
   * (the exact format is configurable).
   *
   * Method uses Date/time/seqNo additions to always create new file, not existent in the metricsReportingBasePath.parent
   * folder before the call.
   *
   * Method also keeps the total number of generated files limited: it removes the oldest files if there are
   * too many files already generated.
   *
   * If metricsReportingBasePath is relative -> method resolves it against IDEA logs folder to make absolute.
   */
  private fun generateFileForMetrics(metricsReportingBasePath: Path): Path {
    val fileLimiterForMetrics = OpenTelemetryUtils.setupFileLimiterForMetrics(metricsReportingBasePath)
    return fileLimiterForMetrics.removeOldFilesBut(maxFilesToKeep, FileSetLimiter.DELETE_ASYNC)
      .createNewFile()
  }
}