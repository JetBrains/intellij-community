// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.google.gson.reflect.TypeToken
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.OutputStream

internal class InstallPluginService : RestService() {
  override fun getServiceName() = "installPlugin"

  override fun isOriginAllowed(request: HttpRequest) = OriginCheckResult.ASK_CONFIRMATION

  var isAvailable = true

  private val trustedPredefinedHosts = setOf(
    "jetbrains.com",
    "marketplace.jetbrains.com",
    "plugins.jetbrains.com",
    "package-search.services.jetbrains.com",
    "package-search.jetbrains.com"
  )

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val pluginId = getStringParameter("pluginId", urlDecoder)
    val passedPluginIds = getStringParameter("pluginIds", urlDecoder)
    val action = getStringParameter("action", urlDecoder)

    if (pluginId.isNullOrBlank() && passedPluginIds.isNullOrBlank()) {
      return productInfo(request, context)
    }

    val pluginIds = if (pluginId.isNullOrBlank()) {
      gson.fromJson(passedPluginIds, object : TypeToken<List<String?>?>() {}.type)
    }
    else {
      listOf(pluginId)
    }

    return when (action) {
      "checkCompatibility" -> checkCompatibility(request, context, pluginIds)
      "install" -> installPlugin(request, context, pluginIds)
      else -> productInfo(request, context)
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  private fun checkCompatibility(
    request: FullHttpRequest,
    context: ChannelHandlerContext,
    pluginIds: List<String>,
  ): Nothing? {
    val marketplaceRequests = MarketplaceRequests.getInstance()
    val compatibleUpdatesInfo = pluginIds
      .mapNotNull { PluginId.findId(it) }
      .map { id -> id.idString to (marketplaceRequests.getLastCompatiblePluginUpdate(id) != null) }
      .let { info ->
        if (info.size != 1) info
        else listOf("compatible" to info[0].second)
      }

    //check if there is an update for this IDE with this ID.
    val out = BufferExposingByteArrayOutputStream()
    val writer = createJsonWriter(out)
    writer.beginObject()
    compatibleUpdatesInfo.forEach {
      val (pluginId, value) = it
      writer.name(pluginId).value(value)
    }
    writer.endObject()
    writer.close()

    send(out, request, context)
    return null
  }

  private fun installPlugin(request: FullHttpRequest,
                            context: ChannelHandlerContext,
                            pluginIds: List<String>): Nothing? {
    val plugins = pluginIds.mapNotNull { PluginId.findId(it) }
    if (isAvailable) {
      isAvailable = false
      val effectiveProject = getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
      ApplicationManager.getApplication().invokeLater(Runnable {
        AppIcon.getInstance().requestAttention(effectiveProject, true)
        installAndEnable(effectiveProject, plugins.toSet(), true) { }
        isAvailable = true
      }, effectiveProject.disposed)
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

    var appName = ApplicationInfo.getInstance().fullApplicationName
    val build = ApplicationInfo.getInstance().build

    if (!PlatformUtils.isIdeaUltimate()) {
      val productName = ApplicationNamesInfo.getInstance().productName
      appName = appName.replace("$productName ($productName)", productName)
      appName = appName.removePrefix("JetBrains ")
    }

    writer.name("name").value(appName)
    writer.name("buildNumber").value(build.asString())
    writer.endObject()
    writer.close()
  }

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isHostInPredefinedHosts(request, trustedPredefinedHosts, "idea.api.install.hosts.trusted")
           || super.isHostTrusted(request, urlDecoder)
  }
}
