// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.fasterxml.jackson.core.JsonFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.diagnostic.MacOSDiagnosticReportDirectories
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.COLLECT_LOGS_NOTIFICATION_GROUP
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector
import com.intellij.ide.troubleshooting.collectDimensionServiceDiagnosticsData
import com.intellij.idea.LoggerFactory
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.troubleshooting.TroubleInfoCollector
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.Compressor
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.jackson.obj
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.*

@ApiStatus.Internal
object LogPacker {
  private const val UPLOADS_SERVICE_URL = "https://uploads.jetbrains.com"

  private val gson: Gson by lazy {
    GsonBuilder()
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .create()
  }

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
    val date = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
    val archive = Files.createTempFile("${productName}-logs-${date}", ".zip")
    try {
      Compressor.Zip(archive).use { zip ->
        coroutineContext.ensureActive()

        LogProvider.EP.extensionList.firstOrNull()?.let { logProvider ->
          logProvider.getAdditionalLogFiles(project).forEach { entry ->
            for (dir in entry.files) {
              if (dir.exists()) {
                val dirPrefix = if (entry.entryName.isNotEmpty()) "${entry.entryName}/${dir.name}" else ""
                zip.addDirectory(dirPrefix, dir)
              }
            }
          }
        }

        coroutineContext.ensureActive()
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

        Path.of(SystemProperties.getUserHome()).forEachDirectoryEntry { path ->
          coroutineContext.ensureActive()
          val name = path.name
          if ((name.startsWith("java_error_in") || name.startsWith("jbr_err_pid")) && !name.endsWith("hprof") && Files.isRegularFile(path)) {
            zip.addFile(name, path)
          }
        }

        if (SystemInfoRt.isMac) {
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

  @RequiresBackgroundThread
  @Throws(IOException::class)
  suspend fun uploadLogs(project: Project?): String {
    return indeterminateStep(IdeBundle.message("uploading.logs.message")) {
      withRawProgressReporter {
        withContext(Dispatchers.IO) {
          val file = packLogs(project)
          checkCanceled()
          val responseJson = requestSign(file.name)
          val uploadUrl = responseJson["url"] as String
          val folderName = responseJson["folderName"] as String
          val headers = responseJson["headers"] as Map<*, *>
          checkCanceled()
          coroutineToIndicator {
            upload(file, uploadUrl, headers)
          }
          val message = IdeBundle.message("collect.logs.notification.sent.success", UPLOADS_SERVICE_URL, folderName)
          Notification(COLLECT_LOGS_NOTIFICATION_GROUP, message, NotificationType.INFORMATION).notify(project)
          folderName
        }
      }
    }
  }

  private fun requestSign(fileName: String): Map<String, Any> {
    return HttpRequests.post("$UPLOADS_SERVICE_URL/sign", HttpRequests.JSON_CONTENT_TYPE)
      .accept(HttpRequests.JSON_CONTENT_TYPE)
      .connect { request ->
        val out = BufferExposingByteArrayOutputStream()
        JsonFactory().createGenerator(out).useDefaultPrettyPrinter().use { writer ->
          writer.obj {
            writer.writeStringField("filename", fileName)
            writer.writeStringField("method", "put")
            writer.writeStringField("contentType", "application/octet-stream")
          }
        }
        request.write(out.toByteArray())
        gson.fromJson(request.reader, object : TypeToken<Map<String, Any?>?>() {}.type)
      }
  }

  private fun upload(file: Path, uploadUrl: String, headers: Map<*, *>) {
    val indicator = ProgressManager.getGlobalProgressIndicator()
    HttpRequests.put(uploadUrl, "application/octet-stream")
      .productNameAsUserAgent()
      .tuner { urlConnection ->
        headers.forEach {
          urlConnection.addRequestProperty(it.key as String, it.value as String)
        }
      }
      .connect {
        val http = it.connection as HttpURLConnection
        val length = file.fileSize()
        http.setFixedLengthStreamingMode(length)
        http.outputStream.use { outputStream ->
          file.inputStream().buffered(64 * 1024).use { inputStream ->
            NetUtils.copyStreamContent(indicator, inputStream, outputStream, length)
          }
        }
      }
  }

  fun getBrowseUrl(folderName: String): String = "$UPLOADS_SERVICE_URL/browse#$folderName"

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
