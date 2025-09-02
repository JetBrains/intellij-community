// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.errorreport.error.InternalEAPException
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.buildData.productInfo.CustomPropertyNames
import com.intellij.platform.ide.productInfo.IdeProductInfo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.zip.GZIPOutputStream

@Service
internal class ITNProxyCoroutineScopeHolder(coroutineScope: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  @JvmField
  val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

  @JvmField
  internal val coroutineScope: CoroutineScope = coroutineScope.childScope("ITNProxy call", dispatcher)
}

internal object ITNProxy {
  internal const val EA_PLUGIN_ID = "com.intellij.sisyphus"

  private const val DEFAULT_USER = "idea_anonymous"
  private const val DEFAULT_PASS = "guest"
  private const val NEW_THREAD_VIEW_URL = "https://jb-web.exa.aws.intellij.net/report/"

  internal val DEVICE_ID: String = DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "EA")

  private val LOG = logger<ITNProxy>()

  private val TEMPLATE: Map<String, String?> by lazy {
    val template = LinkedHashMap<String, String?>()
    template["protocol.version"] = "1.1"
    template["os.cpu.arch"] = if (CpuArch.isEmulated()) "${CpuArch.CURRENT}(emulated)" else "${CpuArch.CURRENT}"
    template["os.name"] = OS.CURRENT.name
    template["os.version"] = OS.CURRENT.version
    template["host.id"] = DEVICE_ID
    template["java.version"] = SystemInfo.JAVA_RUNTIME_VERSION
    template["java.vm.vendor"] = SystemInfo.JAVA_VENDOR
    val appInfo = ApplicationInfoEx.getInstanceEx()
    val namesInfo = ApplicationNamesInfo.getInstance()
    val build = appInfo.build
    var buildNumberWithAllDetails = build.asString()
    if (buildNumberWithAllDetails.startsWith(build.productCode + '-')) {
      buildNumberWithAllDetails = buildNumberWithAllDetails.substring(build.productCode.length + 1)
    }
    template["app.name"] = namesInfo.productName
    template["app.name.full"] = namesInfo.fullProductName
    template["app.name.version"] = appInfo.versionName
    template["app.eap"] = java.lang.Boolean.toString(appInfo.isEAP)
    template["app.build"] = appInfo.apiVersion
    template["app.version.major"] = appInfo.majorVersion
    template["app.version.minor"] = appInfo.minorVersion
    template["app.build.date"] = (appInfo.buildTime.toInstant().toEpochMilli()).toString()
    template["app.build.date.release"] = appInfo.majorReleaseBuildDate.time.time.toString()
    template["app.product.code"] = build.productCode
    template["app.build.number"] = buildNumberWithAllDetails
    IdeProductInfo.getInstance().currentProductInfo.customProperties
      .find { it.key == CustomPropertyNames.GIT_REVISION }
      ?.let { template["app.source.revision"] = it.value }
    template
  }

  @JvmRecord
  internal data class ErrorBean(
    val event: IdeaLoggingEvent,
    val comment: String?,
    val pluginId: String?,
    val pluginName: String?,
    val pluginVersion: String?,
    val lastActionId: String?,
  )

  fun getBrowseUrl(threadId: Long): String? =
    if (PluginManagerCore.isPluginInstalled(PluginId.getId(EA_PLUGIN_ID))) NEW_THREAD_VIEW_URL + threadId
    else null

  private val httpClient by lazy {
    HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofMinutes(2))
      .build()
  }

  @Throws(Exception::class)
  suspend fun sendError(error: ErrorBean, newThreadPostUrl: String): Long {
    val context = currentCoroutineContext()

    val response = post(newThreadPostUrl, createRequest(error))
    val responseCode = response.statusCode()
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw InternalEAPException(DiagnosticBundle.message("error.http.result.code", responseCode))
    }

    context.ensureActive()

    val responseText = response.body()
    if (responseText == "unauthorized") {
      throw InternalEAPException("Authorization failed")
    }
    if (responseText.startsWith("update ")) {
      throw UpdateAvailableException(responseText.substring(7))
    }
    if (responseText.startsWith("message ")) {
      throw InternalEAPException(responseText.substring(8))
    }
    try {
      val reportId = responseText.trim()
      LOG.info("report ID: ${reportId}, host ID: ${DEVICE_ID}")
      return reportId.toLong()
    }
    catch (_: NumberFormatException) {
      throw InternalEAPException(DiagnosticBundle.message("error.itn.returns.wrong.data"))
    }
  }

  @JvmStatic
  val appInfoString: String
    get() {
      val builder = StringBuilder()
      appendAppInfo(builder)
      return builder.toString()
    }

  private fun appendAppInfo(builder: StringBuilder) {
    for ((key, value) in TEMPLATE) {
      append(builder, key, value)
    }
  }

  private fun createRequest(error: ErrorBean): StringBuilder {
    val builder = StringBuilder(8192)
    val eventData = error.event.data
    val appInfo = if (eventData is AbstractMessage) eventData.appInfo else null
    if (appInfo != null) {
      builder.append(appInfo)
    }
    else {
      appendAppInfo(builder)
    }

    append(builder, "user.login", DEFAULT_USER)
    append(builder, "user.password", DEFAULT_PASS)
    JBAccountInfoService.getInstance()?.userData?.email?.takeIf { it.endsWith("@jetbrains.com", ignoreCase = true) }?.let {
      append(builder, "user.email", it)
    }

    val updateSettings = UpdateSettings.getInstance()
    append(builder, "update.channel.status", updateSettings.selectedChannelStatus.code)
    append(builder, "update.ignored.builds", java.lang.String.join(",", updateSettings.ignoredBuildNumbers))
    append(builder, "plugin.id", error.pluginId)
    append(builder, "plugin.name", error.pluginName)
    append(builder, "plugin.version", error.pluginVersion)
    append(builder, "last.action", error.lastActionId)

    append(builder, "error.message", error.event.message?.trim { it <= ' ' } ?: "")
    append(builder, "error.stacktrace", error.event.throwableText)
    append(builder, "error.description", error.comment)
    if (error.event.throwable is RecoveredThrowable) {
      append(builder, "error.redacted", "true")
    }

    for (attachment in error.event.attachments) {
      append(builder, "attachment.name", attachment.name)
      append(builder, "attachment.value", attachment.encodedBytes)
    }
    return builder
  }

  private fun append(builder: StringBuilder, key: String, value: String?) {
    if (!value.isNullOrEmpty()) {
      if (builder.isNotEmpty()) builder.append('&')
      builder.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8))
    }
  }

  @Throws(Exception::class)
  private fun post(url: String, formData: CharSequence): HttpResponse<String> {
    val compressed = BufferExposingByteArrayOutputStream(formData.length)
    OutputStreamWriter(GZIPOutputStream(compressed), StandardCharsets.UTF_8).use { writer ->
      for (element in formData) {
        writer.write(element.code)
      }
    }
    val request = HttpRequest.newBuilder(URI(url))
      .header("Content-Type", "application/x-www-form-urlencoded; charset=" + StandardCharsets.UTF_8.name())
      .header("Content-Encoding", "gzip")
      .POST(HttpRequest.BodyPublishers.ofByteArray(compressed.toByteArray(), 0, compressed.size()))
      .build()
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
  }
}
