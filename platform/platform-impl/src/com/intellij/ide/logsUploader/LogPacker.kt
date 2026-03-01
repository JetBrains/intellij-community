// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.diagnostic.MacOSDiagnosticReportDirectories
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector
import com.intellij.ide.troubleshooting.collectDimensionServiceDiagnosticsData
import com.intellij.idea.LoggerFactory
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.troubleshooting.TroubleInfoCollector
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.Compressor
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@ApiStatus.Internal
@Suppress("UseOptimizedEelFunctions")
object LogPacker {
  @JvmStatic
  @RequiresBackgroundThread
  @Throws(IOException::class)
  suspend fun packLogs(project: Project?): Path = withContext(Dispatchers.IO) {
    val logs = PathManager.getLogDir()
    val caches = PathManager.getSystemDir()
    if (Files.isSameFile(logs, caches)) {
      throw IOException("cannot collect logs, because log directory set to be the same as the 'system' one: $logs")
    }

    PerformanceWatcher.getInstance().dumpThreads("", false, false)
    (Logger.getFactory() as? LoggerFactory)?.flushHandlers()

    val productName = ApplicationNamesInfo.getInstance().productName.lowercase()
    val date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    val archive = Files.createTempFile("${productName}-logs-${date}", ".zip")
    try {
      Compressor.Zip(archive).use { zip ->
        if (project != null) {
          val settings = StringBuilder()
          settings.append(CompositeGeneralTroubleInfoCollector().collectInfo(project))
          for (troubleInfoCollector in TroubleInfoCollector.EP_SETTINGS.extensionList) {
            coroutineContext.ensureActive()
            settings.append(troubleInfoCollector.collectInfo(project)).append('\n')
          }
          zip.addFile("troubleshooting.txt", settings.toString().toByteArray(StandardCharsets.UTF_8))
          zip.addFile("dimension.txt", collectDimensionServiceDiagnosticsData(project).toByteArray(StandardCharsets.UTF_8))
        }

        coroutineContext.ensureActive()

        zip.addDirectory("", logs)

        coroutineContext.ensureActive()

        LogProvider.EP.extensionList.forEach { logProvider ->
          logProvider.getAdditionalLogFiles(project).forEach { entry ->
            entry.files.forEach { dir ->
              coroutineContext.ensureActive()
              if (dir.exists()) {
                val dirPrefix = when {
                  entry.entryName.isEmpty() -> ""
                  entry.createSubdirectories -> "${entry.entryName}/${dir.name}"
                  else -> entry.entryName
                }
                zip.addDirectory(dirPrefix, dir)
              }
            }
          }
        }

        coroutineContext.ensureActive()

        Path.of(SystemProperties.getUserHome()).forEachDirectoryEntry { path ->
          coroutineContext.ensureActive()
          val name = path.name
          if ((name.startsWith("java_error_in") || name.startsWith("jbr_err_pid")) && !name.endsWith("hprof") && Files.isRegularFile(path)) {
            zip.addFile(name, path)
          }
        }

        if (OS.CURRENT == OS.macOS) {
          for (reportDir in MacOSDiagnosticReportDirectories) {
            val dir = Path.of(reportDir)
            if (dir.exists() && dir.isDirectory()) {
              dir.forEachDirectoryEntry { path ->
                coroutineContext.ensureActive()
                val name = path.name
                if (name.endsWith(".ips") && Files.isRegularFile(path) && doesMacOSDiagnosticReportBelongToThisApp(path)) {
                  zip.addFile("MacOS_DiagnosticReports/$name", path)
                }
              }
            }
          }
        }
      }
    }
    catch (e: IOException) {
      try {
        Files.delete(archive)
      }
      catch (x: IOException) {
        e.addSuppressed(x)
      }
      throw e
    }
    archive
  }

  private fun doesMacOSDiagnosticReportBelongToThisApp(path: Path): Boolean {
    val name = path.name
    if (name.contains(ApplicationNamesInfo.getInstance().scriptName, ignoreCase = true)) return true
    if (name.contains("java", ignoreCase = true)) {
      // if IDE is run from sources
      return Files.readString(path).contains("jetbrains", ignoreCase = true)
    }
    return false
  }
}
