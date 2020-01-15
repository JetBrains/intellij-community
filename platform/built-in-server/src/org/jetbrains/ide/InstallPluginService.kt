// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.origin
import com.intellij.util.net.NetUtils
import com.intellij.util.text.nullize
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException

internal class InstallPluginService : RestService() {
  override fun getServiceName() = "installPlugin"

  override fun isAccessible(request: HttpRequest) = true

  var isAvailable = true

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val pluginId = getStringParameter("pluginId", urlDecoder)
    val action = getStringParameter("action", urlDecoder)

    if (pluginId.isNullOrBlank()) {
      return productInfo(request, context)
    }

    return when (action) {
      "checkCompatibility" -> checkCompatibility(request, context, pluginId)
      "install" -> installPlugin(request, context, pluginId)
      else -> productInfo(request, context)
    }
  }

  //TODO: replace with `PluginDownloader.getLastCompatiblePluginUpdate` from IDEA-CR-56320
  private fun getBuildNumberForDownload(): String {
    val app: Application? = ApplicationManager.getApplication()
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    return if (app != null) ApplicationInfo.getInstance().apiVersion else appInfo.build.asString()
  }

  private fun checkCompatibility(request: FullHttpRequest,
                                 context: ChannelHandlerContext,
                                 pluginId: String): Nothing? {
    //check if there is an update for this IDE with this ID. TODO: replace with `getLastCompatiblePluginUpdate` from IDEA-CR-56320
    val url = Urls
      .newFromEncoded(ApplicationInfoImpl.getShadowInstance().pluginManagerUrl.trimEnd('/') + "/api/getCompatibleUpdates")
      .addParameters(mapOf(
        "build" to getBuildNumberForDownload(),
        "pluginXmlId" to pluginId,
        "max" to "1"
      ))
    val compatibleUpdateExists = HttpRequests.request(url).readString() != "[]"

    val out = BufferExposingByteArrayOutputStream()

    val writer = createJsonWriter(out)
    writer.beginObject()
    writer.name("compatible").value(compatibleUpdateExists)
    writer.endObject()
    writer.close()

    send(out, request, context)
    return null
  }

  private fun installPlugin(request: FullHttpRequest,
                            context: ChannelHandlerContext,
                            pluginId: String): Nothing? {
    if (isAvailable) {
      isAvailable = false
      val effectiveProject = getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
      PluginId.findId(pluginId)?.let {
        ApplicationManager.getApplication().invokeLater(Runnable {
          AppIcon.getInstance().requestAttention(effectiveProject, true)
          PluginsAdvertiser.installAndEnable(setOf(it)) { }
          isAvailable = true
        }, effectiveProject.disposed)
      }
    }

    sendOk(request, context)
    return null
  }

  private fun productInfo(request: FullHttpRequest,
                          context: ChannelHandlerContext): Nothing? {
    val out = BufferExposingByteArrayOutputStream()

    writeIDEInfo(out)

    send(out, request, context)
    return null
  }

  private fun writeIDEInfo(out: OutputStream) {
    val writer = createJsonWriter(out)
    writer.beginObject()

    var appName = ApplicationInfoEx.getInstanceEx().fullApplicationName
    val build = ApplicationInfo.getInstance().build

    if (!PlatformUtils.isIdeaUltimate()) {
      val productName = ApplicationNamesInfo.getInstance().productName
      appName = appName.replace("$productName ($productName)", productName)
      appName = StringUtil.trimStart(appName, "JetBrains ")
    }

    writer.name("name").value(appName)
    writer.name("buildNumber").value(build.asString())
    writer.endObject()
    writer.close()
  }

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    val origin = request.origin
    val originHost = try {
      if (origin == null) null else URI(origin).host.nullize()
    }
    catch (ignored: URISyntaxException) {
      return false
    }

    return (originHost != null &&
            (originHost == "plugins.jetbrains.com" ||
             originHost.endsWith(".dev.marketplace.intellij.net") ||
             NetUtils.isLocalhost(originHost))) ||
           super.isHostTrusted(request, urlDecoder)
  }
}