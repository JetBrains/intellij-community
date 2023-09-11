// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.CollectZippedLogsAction
import com.intellij.ide.logsUploader.LogsPacker
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.io.jackson.obj
import com.intellij.util.ui.IoErrorText
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.IOException

private const val propertyKeyForTrustedHosts = "idea.api.collectLogs.hosts.trusted"

class UploadLogsService : RestService() {

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
    if(path == "status") {
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
      val task = object : Task.Backgroundable(project, IdeBundle.message("collect.upload.logs.progress.title"), true) {
        override fun onCancel() {
          sendStatus(HttpResponseStatus.BAD_REQUEST, false, channel)
        }

        override fun run(indicator: ProgressIndicator) {
          try {
            val byteOut = BufferExposingByteArrayOutputStream()
            val uploadedID = LogsPacker.uploadLogs(indicator, project)
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
            sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, false, channel)
          }
        }
      }
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
    }
    return null
  }

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isHostInPredefinedHosts(request, trustedPredefinedHosts, propertyKeyForTrustedHosts)
  }
}