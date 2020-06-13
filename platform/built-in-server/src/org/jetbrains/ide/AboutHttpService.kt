// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.PlatformUtils
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import com.intellij.util.io.origin
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService.Companion.getBooleanParameter
import java.io.OutputStream

/**
 * @api {get} /about The application info
 * @apiName about
 * @apiGroup Platform
 *
 * @apiParam {Boolean} [registeredFileTypes=false] Whether to include the list of registered file types.
 * @apiParam {Boolean} [more=false] Whether to include the full info.
 *
 * @apiSuccess {String} name The full application name.
 * @apiSuccess {String} productName The product name.
 * @apiSuccess {String} baselineVersion The baseline version.
 * @apiSuccess {String} [buildNumber] The build number.
 *
 * @apiSuccess {Object[]} registeredFileTypes The list of registered file types.
 * @apiSuccess {String} registeredFileTypes.name The name of file type.
 * @apiSuccess {String} registeredFileTypes.description The user-readable description of the file type.
 * @apiSuccess {Boolean} registeredFileTypes.isBinary Whether files of the specified type contain binary data.
 *
 * * @apiExample Request-Example:
 * /rest/about?registeredFileTypes
 *
 * @apiUse SuccessExample
 * @apiUse SuccessExampleWithRegisteredFileTypes
 */
internal class AboutHttpService : RestService() {
  override fun getServiceName() = "about"

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    return isTrustedOrigin(request) || super.isHostTrusted(request, urlDecoder)
  }

  override fun isAccessible(request: HttpRequest): Boolean {
    return isTrustedOrigin(request) || super.isAccessible(request)
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val byteOut = BufferExposingByteArrayOutputStream()
    writeApplicationInfoJson(byteOut, urlDecoder)
    send(byteOut, request, context)
    return null
  }
}

private fun isTrustedOrigin(request: HttpRequest): Boolean {
  val origin = request.origin ?: return false
  @Suppress("SpellCheckingInspection")
  return origin == "hyperskill.org"
}

fun writeApplicationInfoJson(out: OutputStream, urlDecoder: QueryStringDecoder?) {
  val writer = JsonFactory().createGenerator(out).useDefaultPrettyPrinter()
  writer.obj {
    writeAboutJson(writer)
    if (urlDecoder != null && getBooleanParameter("registeredFileTypes", urlDecoder)) {
      writer.array("registeredFileTypes") {
        for (fileType in FileTypeRegistry.getInstance().registeredFileTypes) {
          writer.obj {
            writer.writeStringField("name", fileType.name)
            writer.writeStringField("description", fileType.description)
            writer.writeBooleanField("isBinary", fileType.isBinary)
          }
        }
      }
    }
    if (urlDecoder != null && getBooleanParameter("more", urlDecoder)) {
      val appInfo = ApplicationInfoEx.getInstanceEx()
      writer.writeStringField("vendor", appInfo.companyName)
      writer.writeBooleanField("isEAP", appInfo.isEAP)
      writer.writeStringField("productCode", appInfo.build.productCode)
      writer.writeNumberField("buildDate", appInfo.buildDate.time.time)
      writer.writeBooleanField("isSnapshot", appInfo.build.isSnapshot)
      writer.writeStringField("configPath", PathManager.getConfigPath())
      writer.writeStringField("systemPath", PathManager.getSystemPath())
      writer.writeStringField("binPath", PathManager.getBinPath())
      writer.writeStringField("logPath", PathManager.getLogPath())
      writer.writeStringField("homePath", PathManager.getHomePath())
    }
  }
  writer.close()
}

fun writeAboutJson(writer: JsonGenerator) {
  var appName = ApplicationInfoEx.getInstanceEx().fullApplicationName
  if (!PlatformUtils.isIdeaUltimate()) {
    val productName = ApplicationNamesInfo.getInstance().productName
    appName = appName
      .replace("$productName ($productName)", productName)
      .removePrefix("JetBrains ")
  }
  writer.writeStringField("name", appName)
  writer.writeStringField("productName", ApplicationNamesInfo.getInstance().productName)

  val build = ApplicationInfo.getInstance().build
  writer.writeNumberField("baselineVersion", build.baselineVersion)
  if (!build.isSnapshot) {
    writer.writeStringField("buildNumber", build.asStringWithoutProductCode())
  }
}