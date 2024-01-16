// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.fasterxml.jackson.core.JsonFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.diagnostic.PerformanceWatcher.Companion.getInstance
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.CollectZippedLogsAction
import com.intellij.ide.logsUploader.LogProvider.*
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
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.troubleshooting.TroubleInfoCollector
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.addFile
import com.intellij.util.io.addFolder
import com.intellij.util.io.jackson.obj
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.name

@ApiStatus.Internal
object LogsPacker {
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
  suspend fun packLogs(project: Project?): Path {
    return withContext(Dispatchers.IO) {
      getInstance().dumpThreads("", false, false)

      val productName = ApplicationNamesInfo.getInstance().productName.lowercase()
      val date = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
      val archive = Files.createTempFile("$productName-logs-$date", ".zip")
      try {
        val additionalFiles = LogProvider.EP.extensionList.flatMap { it.getAdditionalLogFiles(project) }
        ZipOutputStream(FileOutputStream(archive.toFile())).use { zip ->
          coroutineContext.ensureActive()
          val logs = PathManager.getLogDir()
          val caches = PathManager.getSystemDir()
          if (Files.isSameFile(logs, caches)) {
            throw IOException("cannot collect logs, because log directory set to be the same as the 'system' one: $logs")
          }
          val lf = Logger.getFactory()
          if (lf is LoggerFactory) {
            lf.flushHandlers()
          }

          addAdditionalFilesToZip(additionalFiles, zip)

          coroutineContext.ensureActive()
          if (project != null) {
            val settings = StringBuilder()
            settings.append(CompositeGeneralTroubleInfoCollector().collectInfo(project))
            for (troubleInfoCollector in TroubleInfoCollector.EP_SETTINGS.extensions) {
              coroutineContext.ensureActive()
              settings.append(troubleInfoCollector.collectInfo(project)).append('\n')
            }
            zip.addFile("troubleshooting.txt", settings.toString().toByteArray(StandardCharsets.UTF_8))
            zip.addFile("dimension.txt", collectDimensionServiceDiagnosticsData(project).toByteArray(StandardCharsets.UTF_8))
          }
          Files.newDirectoryStream(Path.of(SystemProperties.getUserHome())).use { paths ->
            for (path in paths) {
              coroutineContext.ensureActive()
              val name = path.fileName.toString()
              if ((name.startsWith("java_error_in") || name.startsWith("jbr_err_pid")) && !name.endsWith("hprof") && Files.isRegularFile(
                  path)) {
                zip.addFolder(name, path)
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
  }

  @RequiresBackgroundThread
  @Throws(IOException::class)
  suspend fun uploadLogs(project: Project?): String {
    return indeterminateStep(IdeBundle.message("uploading.logs.message")) {
      withRawProgressReporter {
        withContext(Dispatchers.IO) {
          val file = packLogs(project)
          checkCancelled()
          val responseJson = requestSign(file.name)
          val uploadUrl = responseJson["url"] as String
          val folderName = responseJson["folderName"] as String
          val headers = responseJson["headers"] as Map<*, *>
          checkCancelled()
          coroutineToIndicator {
            upload(file, uploadUrl, headers)
          }
          val message = IdeBundle.message("collect.logs.notification.sent.success", UPLOADS_SERVICE_URL, folderName)
          Notification(CollectZippedLogsAction.NOTIFICATION_GROUP, message, NotificationType.INFORMATION).notify(project)
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

  private fun addAdditionalFilesToZip(logsEntryList: List<LogsEntry>,
                                      zip: ZipOutputStream) {
    for (additionalFiles in logsEntryList) {
      for (file in additionalFiles.files) {
        if (file.exists()) {
          val entryName = buildEntryName(additionalFiles.entryName, file)
          zip.addFolder(entryName, file)
        }
      }
    }
  }

  /**
   * @return entry name. Empty name is expected for platform logs
   */
  private fun buildEntryName(prefix: String?, file: Path): String {
    return if (!prefix.isNullOrEmpty()) "$prefix/${file.name}" else ""
  }
}