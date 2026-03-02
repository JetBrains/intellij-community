// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.errorreport.error.InternalEAPException
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.UnhandledException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.buildData.productInfo.CustomPropertyNames
import com.intellij.platform.ide.productInfo.IdeProductInfo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.JBAccountInfoService
import com.intellij.ui.LicensingFacade
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
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
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader

@Service
internal class ITNProxyCoroutineScopeHolder(coroutineScope: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  @JvmField
  val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

  @JvmField
  internal val coroutineScope: CoroutineScope = coroutineScope.childScope("ITNProxy call", dispatcher)
}

@ApiStatus.Internal
object ITNProxy {
  private const val DEFAULT_ENDPOINT = "https://ea-report.jetbrains.com/trackerRpc/idea/createScr"
  private const val DIOGEN_VIEW_URL = "https://diogen.labs.jb.gg/report/"

  internal val DEVICE_ID: String by lazy {
    DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "EA")
  }

  private val TEMPLATE_SAFE: Map<String, String?> by lazy {
    val template = LinkedHashMap<String, String?>()
    template["protocol.version"] = "1.1"
    template["user.login"] = "idea_anonymous"
    template["user.password"] = "guest"
    template["os.cpu.arch"] = if (CpuArch.isEmulated()) "${CpuArch.CURRENT}(emulated)" else "${CpuArch.CURRENT}"
    template["os.name"] = OS.CURRENT.name
    template["os.version"] = OS.CURRENT.version()
    template["java.version"] = SystemInfo.JAVA_RUNTIME_VERSION
    template["java.vm.vendor"] = SystemInfo.JAVA_VENDOR
    template
  }

  private val TEMPLATE_APP: Map<String, String?> by lazy {
    val appInfo = ApplicationInfoEx.getInstanceEx()
    val namesInfo = ApplicationNamesInfo.getInstance()
    val build = appInfo.build

    val template = LinkedHashMap<String, String?>()
    template["host.id"] = DEVICE_ID
    template["app.name"] = namesInfo.productName
    template["app.name.full"] = namesInfo.fullProductName
    template["app.name.version"] = appInfo.versionName
    template["app.eap"] = appInfo.isEAP.toString()
    template["app.build"] = appInfo.apiVersion
    template["app.version.major"] = appInfo.majorVersion
    template["app.version.minor"] = appInfo.minorVersion
    template["app.build.date"] = appInfo.buildTime.toInstant().toEpochMilli().toString()
    template["app.build.date.release"] = appInfo.majorReleaseBuildDate.time.time.toString()
    template["app.product.code"] = build.productCode
    template["app.build.number"] = build.asStringWithoutProductCode()
    IdeProductInfo.getInstance().currentProductInfo.customProperties
      .find { it.key == CustomPropertyNames.GIT_REVISION }
      ?.let { template["app.source.revision"] = it.value }
    template
  }

  private fun appendEarlyAppData(builder: StringBuilder) {
    @Suppress("TestOnlyProblems")
    val homeDir = System.getProperty("idea.home.path")?.let { Path(it) }
      ?: PathManager.getHomeDirFor(ITNProxy::class.java)
      ?: throw RuntimeException("Cannot detect the IDE home directory")
    val appDataFile = homeDir.resolve(when {
      AppMode.isRunningFromDevBuild() -> "bin/product-info.json"
      OS.CURRENT == OS.macOS -> "Resources/product-info.json"
      else -> "product-info.json"
    })

    try {
      var appName = null as String?
      var version = null as String?
      var buildNumber = null as String?
      var productCode = null as String?
      JsonFactory().createParser(ObjectReadContext.empty(), appDataFile.bufferedReader()).use { parser ->
        if (parser.nextToken() == JsonToken.START_OBJECT) {
          while (true) {
            if (parser.nextToken() != JsonToken.PROPERTY_NAME) break
            val name = parser.currentName()
            if (parser.nextToken() != JsonToken.VALUE_STRING) break
            val value = parser.string
            when (name) {
              "name" -> appName = value
              "version" -> version = value
              "buildNumber" -> buildNumber = value
              "productCode" -> productCode = value
            }
          }
        }
      }
      if (appName == null || version == null || buildNumber == null || productCode == null) throw RuntimeException("Malformed app data file")

      val shortName = appName.splitToSequence(' ').last()
      val versionParts = version.split('.')
      append(builder, "app.name", shortName)
      append(builder, "app.name.full", appName)
      append(builder, "app.build", "${productCode}-${buildNumber}")
      append(builder, "app.version.major", versionParts[0])
      append(builder, "app.version.minor", versionParts.getOrNull(1) ?: "0")
      append(builder, "app.product.code", productCode)
      append(builder, "app.build.number", buildNumber)
    }
    catch (e: Exception) {
      throw RuntimeException("Cannot read application data", e)
    }
  }

  @JvmRecord
  internal data class ErrorBean(
    val event: IdeaLoggingEvent,
    val comment: String?,
    val pluginId: String?,
    val pluginName: String?,
    val pluginVersion: String?,
    val lastActionId: String?,
    val isAutoReportedByPlatform: Boolean,
  )

  internal fun getBrowseUrl(threadId: Long): String? = when {
    isInternalUser() -> DIOGEN_VIEW_URL + threadId
    else -> null
  }

  private fun isInternalUser(): Boolean {
    val isJetBrainsEmail = JBAccountInfoService.getInstance()?.userData?.email?.endsWith("@jetbrains.com") == true
    val isJetBrainsTeam = LicensingFacade.getInstance()?.licensedTo?.contains("JetBrains Team") == true
    return isJetBrainsEmail || isJetBrainsTeam
  }

  private val httpClient by lazy {
    HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofMinutes(2))
      .build()
  }

  @Throws(Exception::class)
  internal suspend fun sendError(error: ErrorBean, newThreadPostUrl: String?): Long {
    val context = currentCoroutineContext()
    val request = createRequest(error.event, error)
    val response = post(newThreadPostUrl ?: DEFAULT_ENDPOINT, request)
    context.ensureActive()
    val reportId = handleResponse(response)
    logger<ITNProxy>().info("report ID: ${reportId}")
    return reportId
  }

  @JvmStatic
  @Throws(Exception::class)
  fun sendError(event: IdeaLoggingEvent): Long {
    val request = createRequest(event, errorBean = null)
    val response = post(DEFAULT_ENDPOINT, request)
    return handleResponse(response)
  }

  private fun handleResponse(response: HttpResponse<String>): Long {
    val responseCode = response.statusCode()
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw InternalEAPException(DiagnosticBundle.message("error.http.result.code", responseCode))
    }

    val responseText = response.body()
    when {
      responseText == "unauthorized" -> throw InternalEAPException("Authorization failed")
      responseText.startsWith("update ") -> throw UpdateAvailableException(responseText.substring(7))
      responseText.startsWith("message ") -> throw InternalEAPException(responseText.substring(8))
    }
    try {
      return responseText.trim().toLong()
    }
    catch (_: NumberFormatException) {
      throw InternalEAPException(DiagnosticBundle.message("error.itn.returns.wrong.data"))
    }
  }

  @JvmStatic
  internal val appInfoString: String
    get() = StringBuilder().apply { appendAppInfo(this) }.toString()

  private fun appendAppInfo(builder: StringBuilder) {
    TEMPLATE_SAFE.forEach { (key, value) -> append(builder, key, value) }
    TEMPLATE_APP.forEach { (key, value) -> append(builder, key, value) }
  }

  private fun createRequest(event: IdeaLoggingEvent, errorBean: ErrorBean?): StringBuilder {
    val builder = StringBuilder(8192)

    if (errorBean != null) {
      val appInfo = (event.data as? AbstractMessage)?.appInfo
      if (appInfo != null) {
        builder.append(appInfo)
      }
      else {
        appendAppInfo(builder)
        append(builder, "report.startup.error", "true")
      }
    }
    else {
      TEMPLATE_SAFE.forEach { (key, value) -> append(builder, key, value) }
      appendEarlyAppData(builder)
    }

    append(builder, "error.message", event.message?.trim { it <= ' ' } ?: "")
    append(builder, "error.stacktrace", event.throwableText)
    (event.throwable as? UnhandledException)?.let {
      append(builder, "error.unhandled.interactive", it.isInteractive.toString())
    }
    if (event.throwable is RecoveredThrowable) {
      append(builder, "error.redacted", "true")
    }

    for (attachment in event.attachments) {
      append(builder, "attachment.name", attachment.name)
      append(builder, "attachment.value", attachment.encodedBytes)
    }

    // optional fields; added only when the app is loaded
    if (errorBean != null) {
      JBAccountInfoService.getInstance()?.userData?.email?.takeIf { it.endsWith("@jetbrains.com", ignoreCase = true) }?.let {
        append(builder, "user.email", it)
      }

      val updateSettings = UpdateSettings.getInstance()
      append(builder, "update.channel.status", updateSettings.selectedChannelStatus.code)
      append(builder, "update.ignored.builds", updateSettings.ignoredBuildNumbers.joinToString(","))

      append(builder, "plugin.id", errorBean.pluginId)
      append(builder, "plugin.name", errorBean.pluginName)
      append(builder, "plugin.version", errorBean.pluginVersion)
      append(builder, "last.action", errorBean.lastActionId)

      append(builder, "error.description", errorBean.comment)

      PluginManagerCore.loadedPlugins
        .filter { !it.isBundled }
        .map { it.pluginId }
        .filter { getPluginInfoById(it).isSafeToReport() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(",") { it.idString }
        ?.let { append(builder, "plugins.nonbundled", it) }

      if (errorBean.isAutoReportedByPlatform) {
        append(builder, "report.automatic", "true")
      }
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
