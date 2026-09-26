// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.diagnostic.MacOSDiagnosticReportDirectories
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector
import com.intellij.ide.troubleshooting.collectDimensionServiceDiagnosticsData
import com.intellij.idea.LoggerFactory
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.troubleshooting.GeneralTroubleInfoCollector
import com.intellij.troubleshooting.TroubleInfoCollector
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.Compressor
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
@Suppress("UseOptimizedEelFunctions")
@OptIn(LowLevelLocalMachineAccess::class)
object LogPacker {
  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
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
          val settings = serviceAsync<TroubleInfoCollectionService>().collectInfo(project)
          zip.addFile("troubleshooting.txt", settings.toByteArray(StandardCharsets.UTF_8))
          zip.addFile("dimension.txt", collectDimensionServiceDiagnosticsData(project).toByteArray(StandardCharsets.UTF_8))
        }

        coroutineContext.ensureActive()

        zip.addDirectory("", logs)

        coroutineContext.ensureActive()

        LogProvider.EP.forEachExtensionSafe { logProvider ->
          logProvider.getAdditionalLogFiles(project).forEach { entry ->
            entry.files.forEach { path ->
              coroutineContext.ensureActive()
              if (path.isDirectory()) {
                val prefix = when {
                  entry.entryName.isBlank() -> ""
                  entry.createSubdirectories -> "${entry.entryName}/${path.name}"
                  else -> entry.entryName
                }
                zip.addDirectory(prefix, path)
              }
              else if (path.exists()) {
                val prefix = if (entry.entryName.isNotBlank() && entry.createSubdirectories) "${entry.entryName}/" else ""
                zip.addFile("${prefix}${path.name}", path)
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

@Service(Service.Level.APP)
private class TroubleInfoCollectionService(private val coroutineScope: CoroutineScope) {
  /**
   * Process EPs, abandoning the hanging collectors if timeout is set.
   */
  @Suppress("IncorrectCancellationExceptionHandling")
  suspend fun collectInfo(project: Project): String {
    val timeoutSeconds = RegistryManager.getInstanceAsync().intValue("ide.logs.troubleshoot.collector.timeout.seconds")
    val timeout = if (timeoutSeconds < 0) Duration.INFINITE else timeoutSeconds.seconds

    val collectorScope = coroutineScope.childScope("Log collectors", Dispatchers.IO)
    try {
      val infoFromGeneralCollectors = GeneralTroubleInfoCollector.EP_SETTINGS.extensionList.map { collector ->
        asyncCollector(collectorScope, collector.getTitle()) {
          CompositeGeneralTroubleInfoCollector.collectInfo(project, collector)
        }
      }
      val infoFromCollectors = TroubleInfoCollector.EP_SETTINGS.extensionList.map { collector ->
        asyncCollector(collectorScope, collector.toString()) {
          collector.collectInfo(project) + "\n"
        }
      }

      val allCollectors = infoFromGeneralCollectors + infoFromCollectors
      try {
        withTimeout(timeout) {
          allCollectors.map { it.deferred }.joinAll()
        }
      }
      catch (_: TimeoutCancellationException) {
        // the unfinished collectors will be canceled in finally block
      }

      val settings = StringBuilder()
      for (result in allCollectors) {
        if (!result.deferred.isCompleted) {
          // the task is still running after the timeout
          settings.append("=== Collector ${result.collectorPresentation} did not finish in ${timeout} ===\n\n")
          continue
        }
        try {
          val collectorText = result.deferred.await() // completed, returns immediately
          settings.append(collectorText)
        }
        catch (_: CancellationException) {
          currentCoroutineContext().ensureActive() // throws if the current coroutine was cancelled
          settings.append("=== Collector ${result.collectorPresentation} was cancelled ===\n\n")
        }
        catch (e: Throwable) {
          settings.append("=== Collector ${result.collectorPresentation} has failed ===\n\n")
          logger<LogPacker>().error(e)
        }
      }
      return settings.toString()
    }
    finally {
      collectorScope.cancel()
    }
  }

  private fun asyncCollector(
    coroutineScope: CoroutineScope,
    collectorPresentation: String,
    block: suspend CoroutineScope.() -> CharSequence,
  ): AsyncCollectorResult {
    val deferred = coroutineScope.async {
      block()
    }
    return AsyncCollectorResult(deferred, collectorPresentation)
  }
}

private class AsyncCollectorResult(val deferred: Deferred<CharSequence>, val collectorPresentation: String)
