// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.errorreport.error.InternalEAPException
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.security.CompositeX509TrustManager
import com.intellij.util.io.DigestUtil.sha1
import com.intellij.util.net.NetUtils
import com.intellij.util.net.ssl.CertificateUtil
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.GZIPOutputStream
import javax.net.ssl.*

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

  private val DEVICE_ID: String = DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "EA")

  private val TEMPLATE: Map<String, String?> by lazy {
    val template = LinkedHashMap<String, String?>()
    template["protocol.version"] = "1.1"
    template["os.name"] = SystemInfo.OS_NAME
    template["host.id"] = DEVICE_ID
    template["java.version"] = SystemInfo.JAVA_VERSION
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
    val previousException: Int
  )

  fun getBrowseUrl(threadId: Int): String? =
    if (PluginManagerCore.isPluginInstalled(PluginId.getId(EA_PLUGIN_ID))) NEW_THREAD_VIEW_URL + threadId
    else null

  private val ourSslContext: SSLContext by lazy { initContext() }

  suspend fun sendError(error: ErrorBean, newThreadPostUrl: String): Int {
    val context = currentCoroutineContext()
    val connection = post(URL(newThreadPostUrl), createRequest(error))
    val responseCode = connection.responseCode
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw InternalEAPException(DiagnosticBundle.message("error.http.result.code", responseCode))
    }
    context.ensureActive()
    val response: String
    InputStreamReader(connection.inputStream, StandardCharsets.UTF_8).use { reader ->
      response = StreamUtil.readText(reader)
    }
    if ("unauthorized" == response) {
      throw InternalEAPException("Authorization failed")
    }
    if (response.startsWith("update ")) {
      throw UpdateAvailableException(response.substring(7))
    }
    if (response.startsWith("message ")) {
      throw InternalEAPException(response.substring(8))
    }
    try {
      return response.trim().toInt()
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

    val updateSettings = UpdateSettings.getInstance()
    append(builder, "update.channel.status", updateSettings.selectedChannelStatus.code)
    append(builder, "update.ignored.builds", java.lang.String.join(",", updateSettings.ignoredBuildNumbers))
    append(builder, "plugin.id", error.pluginId)
    append(builder, "plugin.name", error.pluginName)
    append(builder, "plugin.version", error.pluginVersion)
    append(builder, "last.action", error.lastActionId)
    if (error.previousException > 0) {
      append(builder, "previous.exception", error.previousException.toString())
    }

    var message = error.event.message?.trim { it <= ' ' } ?: ""
    val stacktrace = error.event.throwableText
    var redacted = false
    if (error.event is IdeaReportingEvent) {
      val originalMessage = error.event.originalMessage?.trim { it <= ' ' } ?: ""
      val originalStacktrace = error.event.originalThrowableText
      val messagesDiffer = message != originalMessage
      val tracesDiffer = stacktrace != originalStacktrace
      if (messagesDiffer || tracesDiffer) {
        var summary = ""
        if (messagesDiffer) summary += "*** message was redacted (" + diff(originalMessage, message) + ")\n"
        if (tracesDiffer) summary += "*** stacktrace was redacted (" + diff(originalStacktrace, stacktrace) + ")\n"
        message = if (!message.isEmpty()) "$summary\n$message"
        else summary.trim { it <= ' ' }
        redacted = true
      }
    }
    append(builder, "error.message", message)
    append(builder, "error.stacktrace", stacktrace)
    append(builder, "error.description", error.comment)
    if (redacted) {
      append(builder, "error.redacted", java.lang.Boolean.toString(true))
    }

    if (eventData is AbstractMessage) {
      for (attachment in eventData.includedAttachments) {
        append(builder, "attachment.name", attachment.name)
        append(builder, "attachment.value", attachment.encodedBytes)
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

  private fun diff(original: String, redacted: String): String {
    return "original:" + wc(original) + " submitted:" + wc(redacted)
  }

  private fun wc(s: String): String {
    return if (s.isEmpty()) "-"
    else StringUtil.splitByLines(s).size.toString() + "/" +
         s.split("[^\\w']+".toRegex()).dropLastWhile { it.isEmpty() }.size + "/" + s.length
  }

  @Throws(IOException::class)
  private fun post(url: URL, formData: CharSequence): HttpURLConnection {
    val connection = url.openConnection() as HttpsURLConnection
    connection.sslSocketFactory = ourSslContext.socketFactory
    if (!NetUtils.isSniEnabled()) {
      connection.hostnameVerifier = EaHostnameVerifier()
    }
    val compressed = BufferExposingByteArrayOutputStream(formData.length)
    OutputStreamWriter(GZIPOutputStream(compressed), StandardCharsets.UTF_8).use { writer ->
      for (element in formData) {
        writer.write(element.code)
      }
    }
    connection.requestMethod = "POST"
    connection.doInput = true
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=" + StandardCharsets.UTF_8.name())
    connection.setRequestProperty("Content-Length", compressed.size().toString())
    connection.setRequestProperty("Content-Encoding", "gzip")
    connection.outputStream.use { out ->
      out.write(compressed.internalBuffer, 0, compressed.size())
    }
    return connection
  }

  @Synchronized
  @Throws(GeneralSecurityException::class, IOException::class)
  private fun initContext(): SSLContext {
    val cf = CertificateFactory.getInstance(CertificateUtil.X509)
    val ca = cf.generateCertificate(ByteArrayInputStream(JB_CA_CERT.toByteArray(StandardCharsets.US_ASCII)))
    val ks = KeyStore.getInstance(CertificateUtil.JKS)
    ks.load(null, null)
    ks.setCertificateEntry("JetBrains CA", ca)
    val jbTmf = TrustManagerFactory.getInstance(CertificateUtil.X509)
    jbTmf.init(ks)
    val sysTmf = TrustManagerFactory.getInstance(CertificateUtil.X509)
    sysTmf.init(null as KeyStore?)
    val ctx = SSLContext.getInstance("TLS")
    val composite: TrustManager = CompositeX509TrustManager(jbTmf.trustManagers, sysTmf.trustManagers)
    ctx.init(null, arrayOf(composite), null)
    return ctx
  }

  private class EaHostnameVerifier : HostnameVerifier {
    override fun verify(hostname: String, session: SSLSession): Boolean {
      try {
        val certificates = session.peerCertificates
        if (certificates.size > 1) {
          val certificate = certificates[0]
          if (certificate is X509Certificate) {
            val cn = CertificateUtil.getCommonName(certificate)
            if (cn.endsWith(".jetbrains.com") || cn.endsWith(".intellij.net")) {
              return true
            }
          }
          val ca = certificates[certificates.size - 1]
          if (ca is X509Certificate) {
            val cn = CertificateUtil.getCommonName(ca)
            val digest = sha1().digest(ca.encoded)
            val fp = StringBuilder(2 * digest.size)
            for (b in digest) fp.append(Integer.toHexString(b.toInt() and 0xFF))
            if (JB_CA_CN == cn && JB_CA_FP.contentEquals(fp)) {
              return true
            }
          }
        }
      }
      catch (_: SSLPeerUnverifiedException) { }
      catch (_: CertificateEncodingException) { }
      return false
    }
  }

  @Suppress("SpellCheckingInspection")
  private val JB_CA_CERT = """
      -----BEGIN CERTIFICATE-----
      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
      -----END CERTIFICATE-----

      """.trimIndent()

  private const val JB_CA_CN = "JetBrains Enterprise CA"
  private const val JB_CA_FP = "604d3c703a13a3be2d452f14442be11b37e186f"
}
