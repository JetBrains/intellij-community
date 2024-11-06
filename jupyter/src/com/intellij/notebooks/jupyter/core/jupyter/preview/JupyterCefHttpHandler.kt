// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notebooks.jupyter.core.jupyter.preview


import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.toCanonicalPath
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
import java.nio.file.Path

/**
 * "Web server" to serve Jupyter HTML
 */
abstract class JupyterCefHttpHandlerBase(private val absolutePathFiles: Collection<String> = emptyList()) : HttpRequestHandler() {

  companion object {
    private val allowedTypes = setOf("css", "js", "html", "svg", "woff", "woff2", "ttf")
    private const val JUPYTER_HTTP_URI = "jupyter"
    private const val PATH_PREFIX = "/$JUPYTER_HTTP_URI"

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
    private fun getResource(javaClass: Class<*>, path: String): URL {
      // After optimizations in PluginClassLoader, classLoader.getResource return null in debug,
      // so we have additional logic with PluginClassLoader.pluginDescriptor. This is only for debugging purposes.
      var url = javaClass.classLoader.getResource(path)
                ?: (javaClass.classLoader as? PluginAwareClassLoader)?.pluginDescriptor?.getPluginPath()?.let { Path.of(it.toCanonicalPath(), path) }?.toUri()?.toURL()

      // In remote dev, when we running remote-front via 'split (dev-build) run config, we have:
      // (javaClass.classLoader as PluginClassLoader).getAllParents().mapNotNull{it as? PluginClassLoader}.map() { loader -> loader.pluginDescriptor.pluginPath }
      // = out/classes/production/intellij.jupyter.plugin.frontend or out/classes/production/intellij.notebooks.plugin
      // PathUtil.getJarPathForClass(javaClass) = out/classes/production/intellij.jupyter.core
      // But our resources lie not in out/classes but in out/dev-run
      if (url.toString().contains("out/classes/production/intellij.jupyter.plugin.frontend")) {
        url = URL(url.toString().replace("out/classes/production/intellij.jupyter.plugin.frontend", "out/dev-run/Python/plugins/jupyter-plugin"))
      }

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
    return super.isSupported(request) && request.uri().let { it.startsWith(PATH_PREFIX) || it in absolutePathFiles }
  }

  override fun process(urlDecoder: QueryStringDecoder,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext): Boolean {
    val str = request.uri()
    val fullUri = URI(str).path
    val uri = getFileFromUrl(fullUri) ?: return false
    val readBytes = processInternalLibs(uri) ?: return false
    sendData(readBytes, "$appName/$uri", request, context.channel(), EmptyHttpHeaders.INSTANCE)
    return true
  }

  fun processInternalLibs(uri: String): ByteArray? {
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
      thisLogger().warn("Cannot process: ${uri}", t)
      return null
    }
  }

  private fun getFileFromUrl(fullUri: String): String? {
    if (fullUri in absolutePathFiles) {
      return fullUri
    }
    if (!fullUri.startsWith(PATH_PREFIX)) {
      return null
    }
    val uri = fullUri.replace("//", "/").substring(PATH_PREFIX.length).trimStart('/')
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
    val resource = getResource(this::class.java, "$appName/$file")
    return resource.readBytes()
  }
}