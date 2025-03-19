// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.COLLECT_LOGS_NOTIFICATION_GROUP
import com.intellij.ide.actions.ReportFeedbackService
import com.intellij.ide.logsUploader.LogPacker
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.io.jackson.obj
import com.intellij.util.ui.IoErrorText
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

private const val propertyKeyForTrustedHosts = "idea.api.collectLogs.hosts.trusted"

private class UploadLogsService : RestService() {
  private val trustedPredefinedHosts = setOf("intellij-support.jetbrains.com")
  private val serviceName = "logs"

  override fun getServiceName(): String {
    return serviceName
  }

  override fun isOriginAllowed(request: HttpRequest): OriginCheckResult  {
    return if(isHostInPredefinedHosts(request, trustedPredefinedHosts, propertyKeyForTrustedHosts)){
      OriginCheckResult.ALLOW
    } else {
      OriginCheckResult.FORBID
    }
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val path = urlDecoder.path().split(serviceName).last().trimStart('/')
    if (path == "status") {
      sendOk(request, context)
      return null
    }
    val channel = context.channel()
    if (path != "uploads") {
      sendStatus(HttpResponseStatus.BAD_REQUEST, false, channel)
      return null
    }
    val project = getLastFocusedOrOpenedProject()
    if (project != null) {
      service<ReportFeedbackService>().coroutineScope.launch {
        try {
          withBackgroundProgress(project, IdeBundle.message("collect.upload.logs.progress.title"), true) {
            try {
              val byteOut = BufferExposingByteArrayOutputStream()
              val uploadedID = LogPacker.uploadLogs(project)
              JsonFactory().createGenerator(byteOut).useDefaultPrettyPrinter().use { writer ->
                writer.obj {
                  writer.writeStringField("Upload_id", uploadedID)
                }
              }
              send(byteOut, request, context)
            }
            catch (_: CancellationException) {
              sendStatus(HttpResponseStatus.BAD_REQUEST, false, channel)
            }
          }
        }
        catch (x: IOException) {
          val message = IdeBundle.message("collect.logs.notification.error", IoErrorText.message(x))
          Notification(COLLECT_LOGS_NOTIFICATION_GROUP, message, NotificationType.ERROR).notify(project)
          sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, false, channel)
        }
      }
    }
    return null
  }

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isHostInPredefinedHosts(request, trustedPredefinedHosts, propertyKeyForTrustedHosts)
  }
}
