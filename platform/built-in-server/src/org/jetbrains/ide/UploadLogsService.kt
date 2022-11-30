// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.fasterxml.jackson.core.JsonFactory
import com.google.gson.reflect.TypeToken
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.CollectZippedLogsAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.jackson.obj
import com.intellij.util.net.NetUtils
import com.intellij.util.ui.IoErrorText
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.io.path.name

class UploadLogsService : RestService() {
  private val uploadsServiceUrl = "https://uploads.jetbrains.com"

  private val trustedPredefinedHosts = setOf("intellij-support.jetbrains.com")

  override fun getServiceName(): String {
    return "uploadLogs"
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val project = ProjectManager.getInstance().openProjects.firstOrNull()
    if (project != null) {
      val task = object : Task.Backgroundable(project, IdeBundle.message("collect.upload.logs.progress.title"), true) {
        override fun run(indicator: ProgressIndicator) {
          try {
            val byteOut = BufferExposingByteArrayOutputStream()
            val logs = CollectZippedLogsAction.packLogs(project)
            val uploadedID = uploadFile(logs.toFile(), logs.name, indicator)
            if (uploadedID == null) {
              sendStatus(HttpResponseStatus.BAD_REQUEST, false, context.channel())
              return
            }
            val message = IdeBundle.message("collect.logs.notification.sent.success", uploadsServiceUrl, uploadedID)
            Notification(CollectZippedLogsAction.NOTIFICATION_GROUP, message, NotificationType.INFORMATION).notify(project)
            JsonFactory().createGenerator(byteOut).useDefaultPrettyPrinter().use { writer ->
              writer.obj {
                writer.writeStringField("Upload_id", uploadedID)
              }
            }
            send(byteOut, request, context)
          }
          catch (x: IOException) {
            val message = IdeBundle.message("collect.logs.notification.error", IoErrorText.message(x))
            Notification(CollectZippedLogsAction.NOTIFICATION_GROUP, message, NotificationType.ERROR).notify(project)
            sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, false, context.channel())
          }
          catch (pc: ProcessCanceledException) {
            sendStatus(HttpResponseStatus.BAD_REQUEST, false, context.channel())
          }
        }
      }
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
    }
    return null
  }

  fun uploadFile(file: File, fileName: String, indicator: ProgressIndicator): String? {
    if (indicator.isCanceled) {
      return null
    }
    val responseJson = HttpRequests.post("$uploadsServiceUrl/sign", HttpRequests.JSON_CONTENT_TYPE)
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
        gson.fromJson<Map<String, Any>>(request.reader, object : TypeToken<Map<String, Any?>?>() {}.type)
      }

    val uploadUrl = responseJson["url"] as String
    val folderName = responseJson["folderName"] as String
    val headers = responseJson["headers"] as Map<*, *>
    if (indicator.isCanceled) {
      return null
    }
    HttpRequests.put(uploadUrl, "application/octet-stream")
      .productNameAsUserAgent()
      .tuner { urlConnection ->
        headers.forEach {
          urlConnection.addRequestProperty(it.key as String, it.value as String)
        }
      }
      .connect {
        val http = it.connection as HttpURLConnection
        val length = file.length().toInt()
        http.setFixedLengthStreamingMode(length)
        http.outputStream.use { outputStream ->
          file.inputStream().buffered(64 * 1024).use { inputStream ->
            NetUtils.copyStreamContent(indicator, inputStream, outputStream, length.toLong())
          }
        }
      }

    return folderName
  }

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isHostInPredefinedHosts(request, urlDecoder, trustedPredefinedHosts, "idea.api.collectLogs.hosts.trusted")
           || super.isHostTrusted(request, urlDecoder)
  }

}