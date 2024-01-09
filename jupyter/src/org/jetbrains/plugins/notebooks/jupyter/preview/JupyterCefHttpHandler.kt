// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.notebooks.jupyter.preview


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.ide.HttpRequestHandler
import java.io.File
import java.net.URI
import java.net.URL


/**
 * "Web server" to serve Jupyter HTML
 */
abstract class JupyterCefHttpHandlerBase(private val absolutePathFiles: Collection<String> = emptyList()) : HttpRequestHandler() {

  companion object {
    private val allowedTypes = setOf("css", "js", "html", "svg", "woff2", "ttf")
    private const val JUPYTER_HTTP_URI = "jupyter"
    private const val prefix = "/$JUPYTER_HTTP_URI"

    /**
     * Jupyter HTTP files can be accessed with this url
     */
    fun getJupyterHttpUrl(): URIBuilder = getJupyterBaseUrl("http").addPathSegment(JUPYTER_HTTP_URI)

    /**
     * Root of the web app
     */
    fun getIndexUrl(): URIBuilder = getJupyterHttpUrl().addPathSegment("index.html")

    /**
     * Resources are in different folders when launched locally versus installation.
     * This method handles this difference. See .groovy build scripts
     */
    private fun getResource(path: String): URL {
      val javaClass = Companion::class.java
      val url = javaClass.classLoader.getResource(path)
      if (url != null) {
        return url
      }
      val myPath = PathUtil.getJarPathForClass(javaClass)
      // static/css/.. <--our resource
      // lib/python.jar <--getJarPathForClass
      val result = File(File(myPath).parentFile.parentFile, path)
      return result.toURI().toURL()
    }
  }

  override fun isSupported(request: FullHttpRequest): Boolean {
    return super.isSupported(request) && request.uri().let { it.startsWith(prefix) || it in absolutePathFiles }
  }

  override fun process(urlDecoder: QueryStringDecoder,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext): Boolean {
    val str = request.uri()
    val fullUri = URI(str).path
    val uri = getFileFromUrl(fullUri) ?: return false

    val readBytes = processInternalLibs(request, uri) ?: return false
    sendData(readBytes, "$appName/$uri", request, context.channel(), EmptyHttpHeaders.INSTANCE)
    return true
  }

  fun processInternalLibs(request: FullHttpRequest, uri: String): ByteArray? {
    try {
      val extension = FileUtilRt.getExtension(uri)
      // map files used for debugging
      if (extension in allowedTypes || (ApplicationManager.getApplication().isInternal && extension == "map")) {
        return readFile(uri)
      }
      else {
        thisLogger().info("Extension not allowed: $extension")
      }
      return null
    }
    catch (t: Throwable) {
      thisLogger().info("Cannot process: ${request.uri()}", t)
      return null
    }
  }

  private fun getFileFromUrl(fullUri: String): String? {
    if (fullUri in absolutePathFiles) {
      return fullUri
    }
    if (!fullUri.startsWith(prefix)) {
      return null
    }
    val uri = fullUri.replace("//", "/").substring(prefix.length).trimStart('/')
    if (uri.isEmpty()) {
      return null
    }

    if (".." in uri) {
      return null
    }
    return uri
  }

  abstract val appName: String

  private fun readFile(file: String): ByteArray? {
    //Ignore this one because it is used for Widget support
    if (file.contains("BASE_EXTENSION_PATH"))
      return null
    val appName = appName
    val resource = getResource("$appName/$file")
    return resource.readBytes()
  }
}
