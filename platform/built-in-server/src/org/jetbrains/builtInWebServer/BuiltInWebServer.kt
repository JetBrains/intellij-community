// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.google.common.net.InetAddresses
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.ui.ProductIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.endsWithName
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.util.io.*
import com.intellij.util.net.NetUtils
import com.intellij.util.ui.ImageUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.apache.commons.imaging.ImageFormats
import org.apache.commons.imaging.Imaging
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses
import org.jetbrains.io.addNoCache
import org.jetbrains.io.response
import org.jetbrains.io.send
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isDirectory

const val TOKEN_PARAM_NAME: String = "_ijt"
const val TOKEN_HEADER_NAME: String = "x-ijt"

internal val LOG = logger<BuiltInWebServer>()

/**
 * Path handlers help the [BuiltInWebServer] serve requests composed by[com.intellij.ide.browsers.WebBrowserService.getUrlsToOpen].
 *
 * By default, [WebServerPathToFileManager] will be used to map the request to a file.
 * If a file physically exists in the file system, you must use [WebServerRootsProvider].
 */
interface WebServerPathHandler {
  /**
   * Processes the given path request for the specified project
   * (e.g., `http://localhost:63342/<project>/<path>` or `http://<project>.localhost:63342/<path>`).
   *
   * @param path the path of the request; does not include the project name
   * @param project the project associated with the request
   * @param projectName the name of the project
   * @param authHeaders HTTP headers containing authentication information (should be added to a response)
   * @param isCustomHost `false` when a project name is a part of the request path (`/project/path`), `true` otherwise
   * @return `true` if a response has been sent, `false` otherwise
   */
  fun process(
    path: String,
    project: Project,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
    projectName: String,
    authHeaders: HttpHeaders,
    isCustomHost: Boolean,
  ): Boolean
}

internal class BuiltInWebServer : HttpRequestHandler() {
  private val PATH_HANDLER_EP_NAME = ExtensionPointName.create<WebServerPathHandler>("org.jetbrains.webServerPathHandler")

  private val authService = service<BuiltInWebServerAuth>()

  override fun isAccessible(request: HttpRequest): Boolean =
    BuiltInServerOptions.getInstance().builtInServerAvailableExternally ||
    request.isLocalOrigin(onlyAnyOrLoopback = false, hostsOnly = true)

  override fun isSupported(request: FullHttpRequest): Boolean = super.isSupported(request) || request.method() === HttpMethod.POST

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val decodedPath = urlDecoder.path()
    if (sendDefaultFavIcon(decodedPath, request, context)) {
      return true
    }

    var hostName = getHostName(request) ?: return false
    val isIpv6 = hostName.startsWith('[') && hostName.endsWith(']')
    if (isIpv6) {
      hostName = hostName.substring(1, hostName.length - 1)
    }
    val projectNameAsHost =
      if (isIpv6 || InetAddresses.isInetAddress(hostName) || isOwnHostName(hostName) || hostName.endsWith(".ngrok.io")) {
        if (decodedPath.length < 2) {
          return false
        }
        null
      }
      else if (hostName.endsWith(".localhost")) {
        hostName.take(hostName.lastIndexOf('.'))
      }
      else {
        hostName
      }

    val isCustomHost = projectNameAsHost != null
    var offset = if (isCustomHost) 0 else decodedPath.indexOf('/', 1)
    var projectName = if (isCustomHost) projectNameAsHost else decodedPath.substring(1, if (offset == -1) decodedPath.length else offset)
    var isEmptyPath = if (isCustomHost) decodedPath.isEmpty() else offset == -1

    val referer = request.headers().get(HttpHeaderNames.REFERER)
    val projectNameFromReferer =
      if (!isCustomHost && referer != null) {
        try {
          val uri = URI.create(referer)
          val refererPath = uri.path
          if (refererPath != null && refererPath.startsWith('/')) {
            val secondSlashOffset = refererPath.indexOf('/', 1)
            if (secondSlashOffset > 1) refererPath.substring(1, secondSlashOffset)
            else null
          }
          else null
        }
        catch (_: Throwable) {
          null
        }
      }
      else null

    var candidateByDirectoryName: Project? = null
    var isCandidateFromReferer = false
    val project = ProjectManager.getInstance().openProjects.firstOrNull(fun(project: Project): Boolean {
      if (project.isDisposed) return false

      val name = project.name
      if (isCustomHost) {
        // domain name is case-insensitive
        if (projectName.equals(name, ignoreCase = true)) {
          if (!SystemInfo.isFileSystemCaseSensitive) {
            // may be passed path is not correct
            projectName = name
          }
          return true
        }
      }
      else {
        // WEB-17839 Internal web server reports 404 when serving files from a project with slashes in name
        if (decodedPath.regionMatches(1, name, 0, name.length, !SystemInfo.isFileSystemCaseSensitive)) {
          val isEmptyPathCandidate = decodedPath.length == (name.length + 1)
          if (isEmptyPathCandidate || decodedPath[name.length + 1] == '/') {
            projectName = name
            offset = name.length + 1
            isEmptyPath = isEmptyPathCandidate
            return true
          }
        }
      }

      if (candidateByDirectoryName == null && compareNameAndProjectBasePath(projectName, project)) {
        candidateByDirectoryName = project
      }
      if (candidateByDirectoryName == null &&
          projectNameFromReferer != null &&
          (projectNameFromReferer == name || compareNameAndProjectBasePath(projectNameFromReferer, project))) {
        candidateByDirectoryName = project
        isCandidateFromReferer = true
      }
      return false
    }) ?: candidateByDirectoryName

    if (isCandidateFromReferer) {
      projectName = projectNameFromReferer!!
      offset = 0
      isEmptyPath = false
    }

    if (isEmptyPath) {
      // we must redirect "jsdebug" to "jsdebug/" as Nginx does -
      // otherwise the browser will treat it as a file instead of a directory, so a relative path won't work
      redirectToDirectory(request, context.channel(), extraHeaders = null)
      return true
    }

    val authHeaders = authService.validateToken(request) ?: return false

    if (project == null) return false

    if (request.headers().get("Service-Worker") == "script" && !TrustedProjects.isProjectTrusted(project)) {
      return false
    }

    val path = decodedPath.substring(offset).takeIf { it.startsWith('/') }?.let { FileUtil.toCanonicalPath(it).substring(1) } ?: run {
      HttpResponseStatus.NOT_FOUND.send(context.channel(), request, extraHeaders = authHeaders)
      return true
    }

    for (pathHandler in PATH_HANDLER_EP_NAME.extensionList) {
      LOG.runAndLogException {
        if (pathHandler.process(path, project, request, context, projectName, authHeaders, isCustomHost)) {
          return true
        }
      }
    }

    // we registered as a last handler, so we should just return `404` and send extra headers
    HttpResponseStatus.NOT_FOUND.send(context.channel(), request, extraHeaders = authHeaders)
    return true
  }

  private fun sendDefaultFavIcon(rawPath: String, request: FullHttpRequest, context: ChannelHandlerContext): Boolean = when (rawPath) {
    "/favicon.ico" -> {
      val icon = ProductIcons.getInstance().productIcon
      val image =
        (icon as? CachedImageIcon)?.getRealImage() as? BufferedImage
        ?: ImageUtil.createImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB).apply {
          icon.paintIcon(null, graphics, 0, 0)
        }
      val icoBytes = Imaging.writeImageToBytes(image, ImageFormats.ICO, null)
      response(FileResponses.getContentType(rawPath), Unpooled.wrappedBuffer(icoBytes))
        .addNoCache()
        .send(context.channel(), request)
      true
    }
    "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png" -> {
      HttpResponseStatus.NOT_FOUND.send(context.channel(), request)
      true
    }
    else -> false
  }
}

@Deprecated("Please use `BuiltInWebServerAuth.acquireToken` instead")
fun acquireToken(): String = service<BuiltInWebServerAuth>().acquireToken()

fun compareNameAndProjectBasePath(projectName: String, project: Project): Boolean {
  val basePath = project.basePath
  return basePath != null && endsWithName(basePath, projectName)
}

fun findIndexFile(basedir: VirtualFile): VirtualFile? {
  val children = basedir.children
  if (children.isNullOrEmpty()) {
    return null
  }

  for (indexNamePrefix in arrayOf("index.", "default.")) {
    var index: VirtualFile? = null
    val preferredName = indexNamePrefix + "html"
    for (child in children) {
      if (!child.isDirectory) {
        val name = child.name
        //noinspection IfStatementWithIdenticalBranches
        if (name == preferredName) {
          return child
        }
        else if (index == null && name.startsWith(indexNamePrefix)) {
          index = child
        }
      }
    }
    if (index != null) {
      return index
    }
  }
  return null
}

fun findIndexFile(basedir: Path): Path? {
  val children = basedir.directoryStreamIfExists({
    val name = it.fileName.toString()
    name.startsWith("index.") || name.startsWith("default.")
  }) { it.toList() } ?: return null

  for (indexNamePrefix in arrayOf("index.", "default.")) {
    var index: Path? = null
    val preferredName = "${indexNamePrefix}html"
    for (child in children) {
      if (!child.isDirectory()) {
        val name = child.fileName.toString()
        if (name == preferredName) {
          return child
        }
        else if (index == null && name.startsWith(indexNamePrefix)) {
          index = child
        }
      }
    }
    if (index != null) {
      return index
    }
  }
  return null
}

// is host loopback/any or network interface address (i.e., not custom domain)
// must be not used to check is host on local machine
internal fun isOwnHostName(host: String): Boolean {
  if (NetUtils.isLocalhost(host)) {
    return true
  }

  try {
    val address = InetAddress.getByName(host)
    if (host == address.hostAddress || host.equals(address.canonicalHostName, ignoreCase = true)) {
      return true
    }

    val localHostName = InetAddress.getLocalHost().hostName
    // WEB-8889: "host.local" is an own host name; "host." equals to "host.domain" (canonical host name)
    return localHostName.equals(host, ignoreCase = true) ||
           host.endsWith(".local") && localHostName.regionMatches(0, host, 0, host.length - ".local".length, ignoreCase = true)
  }
  catch (_: IOException) {
    return false
  }
}

internal fun redirectToDirectory(request: HttpRequest, channel: Channel, extraHeaders: HttpHeaders?) {
  val response = HttpResponseStatus.MOVED_PERMANENTLY.response(request)
  val url = VfsUtil.toUri("${channel.uriScheme}://${request.host!!}${URI(request.uri()).path}/")!!
  response.headers().add(HttpHeaderNames.LOCATION, url.toASCIIString())
  response.send(channel, request, extraHeaders)
}
