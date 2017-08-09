/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.builtInWebServer

import com.google.common.cache.CacheBuilder
import com.google.common.net.InetAddresses
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.endsWithName
import com.intellij.openapi.util.io.setOwnerPermissions
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.*
import com.intellij.util.net.NetUtils
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.cookie.DefaultCookie
import io.netty.handler.codec.http.cookie.ServerCookieDecoder
import io.netty.handler.codec.http.cookie.ServerCookieEncoder
import org.jetbrains.ide.BuiltInServerManagerImpl
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.orInSafeMode
import org.jetbrains.io.send
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

internal val LOG = Logger.getInstance(BuiltInWebServer::class.java)

// name is duplicated in the ConfigImportHelper
private const val IDE_TOKEN_FILE = "user.web.token"

private val notificationManager by lazy {
  SingletonNotificationManager(BuiltInServerManagerImpl.NOTIFICATION_GROUP.value, NotificationType.INFORMATION,
                                                         null)
}

class BuiltInWebServer : HttpRequestHandler() {
  override fun isAccessible(request: HttpRequest) = request.isLocalOrigin(onlyAnyOrLoopback = false, hostsOnly = true)

  override fun isSupported(request: FullHttpRequest) = super.isSupported(request) || request.method() == HttpMethod.POST

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    var host = request.host
    if (host.isNullOrEmpty()) {
      return false
    }

    val portIndex = host!!.indexOf(':')
    if (portIndex > 0) {
      host = host.substring(0, portIndex)
    }

    val projectName: String?
    val isIpv6 = host[0] == '[' && host.length > 2 && host[host.length - 1] == ']'
    if (isIpv6) {
      host = host.substring(1, host.length - 1)
    }

    if (isIpv6 || InetAddresses.isInetAddress(host) || isOwnHostName(host) || host.endsWith(".ngrok.io")) {
      if (urlDecoder.path().length < 2) {
        return false
      }
      
      projectName = null
    }
    else {
      if (host.endsWith(".localhost")) {
        projectName = host.substring(0, host.lastIndexOf('.'))
      }
      else {
        projectName = host
      }
    }
    return doProcess(urlDecoder, request, context, projectName)
  }
}

internal fun isActivatable() = Registry.`is`("ide.built.in.web.server.activatable", false)

const val TOKEN_PARAM_NAME = "_ijt"
const val TOKEN_HEADER_NAME = "x-ijt"

private val STANDARD_COOKIE by lazy {
  val productName = ApplicationNamesInfo.getInstance().lowercaseProductName
  val configPath = PathManager.getConfigPath()
  val file = Paths.get(configPath, IDE_TOKEN_FILE)
  var token: String? = null
  if (file.exists()) {
    try {
      token = UUID.fromString(file.readText()).toString()
    }
    catch (e: Exception) {
      LOG.warn(e)
    }
  }
  if (token == null) {
    token = UUID.randomUUID().toString()
    file.write(token!!)
    file.setOwnerPermissions()
  }

  // explicit setting domain cookie on localhost doesn't work for chrome
  // http://stackoverflow.com/questions/8134384/chrome-doesnt-create-cookie-for-domain-localhost-in-broken-https
  val cookie = DefaultCookie(productName + "-" + Integer.toHexString(configPath.hashCode()), token!!)
  cookie.isHttpOnly = true
  cookie.setMaxAge(TimeUnit.DAYS.toSeconds(365 * 10))
  cookie.setPath("/")
  cookie
}

// expire after access because we reuse tokens
private val tokens = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build<String, Boolean>()

fun acquireToken(): String {
  var token = tokens.asMap().keys.firstOrNull()
  if (token == null) {
    token = TokenGenerator.generate()
    tokens.put(token, java.lang.Boolean.TRUE)
  }
  return token
}

// http://stackoverflow.com/a/41156 - shorter than UUID, but secure
private object TokenGenerator {
  private val random = SecureRandom()

  fun generate(): String = BigInteger(130, random).toString(32)
}

private fun doProcess(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext, projectNameAsHost: String?): Boolean {
  val decodedPath = URLUtil.unescapePercentSequences(urlDecoder.path())
  var offset: Int
  var isEmptyPath: Boolean
  val isCustomHost = projectNameAsHost != null
  var projectName: String
  if (isCustomHost) {
    projectName = projectNameAsHost!!
    // host mapped to us
    offset = 0
    isEmptyPath = decodedPath.isEmpty()
  }
  else {
    offset = decodedPath.indexOf('/', 1)
    projectName = decodedPath.substring(1, if (offset == -1) decodedPath.length else offset)
    isEmptyPath = offset == -1
  }

  var candidateByDirectoryName: Project? = null
  val project = ProjectManager.getInstance().openProjects.firstOrNull(fun(project: Project): Boolean {
    if (project.isDisposed) {
      return false
    }

    val name = project.name
    if (isCustomHost) {
      // domain name is case-insensitive
      if (projectName.equals(name, ignoreCase = true)) {
        if (!SystemInfoRt.isFileSystemCaseSensitive) {
          // may be passed path is not correct
          projectName = name
        }
        return true
      }
    }
    else {
      // WEB-17839 Internal web server reports 404 when serving files from project with slashes in name
      if (decodedPath.regionMatches(1, name, 0, name.length, !SystemInfoRt.isFileSystemCaseSensitive)) {
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
    return false
  }) ?: candidateByDirectoryName ?: return false

  if (isActivatable() && !PropertiesComponent.getInstance().getBoolean("ide.built.in.web.server.active")) {
    notificationManager.notify("Built-in web server is deactivated, to activate, please use Open in Browser", null)
    return false
  }

  if (isEmptyPath) {
    // we must redirect "jsdebug" to "jsdebug/" as nginx does, otherwise browser will treat it as a file instead of a directory, so, relative path will not work
    redirectToDirectory(request, context.channel(), projectName, null)
    return true
  }

  val path = toIdeaPath(decodedPath, offset)
  if (path == null) {
    HttpResponseStatus.BAD_REQUEST.orInSafeMode(HttpResponseStatus.NOT_FOUND).send(context.channel(), request)
    return true
  }

  for (pathHandler in WebServerPathHandler.EP_NAME.extensions) {
    LOG.runAndLogException {
      if (pathHandler.process(path, project, request, context, projectName, decodedPath, isCustomHost)) {
        return true
      }
    }
  }
  return false
}

fun HttpRequest.isSignedRequest(): Boolean {
  if (BuiltInServerOptions.getInstance().allowUnsignedRequests) {
    return true
  }

  // we must check referrer - if html cached, browser will send request without query
  val token = headers().get(TOKEN_HEADER_NAME)
      ?: QueryStringDecoder(uri()).parameters().get(TOKEN_PARAM_NAME)?.firstOrNull()
      ?: referrer?.let { QueryStringDecoder(it).parameters().get(TOKEN_PARAM_NAME)?.firstOrNull() }

  // we don't invalidate token - allow to make subsequent requests using it (it is required for our javadoc DocumentationComponent)
  return token != null && tokens.getIfPresent(token) != null
}

fun validateToken(request: HttpRequest, channel: Channel, isSignedRequest: Boolean): HttpHeaders? {
  if (BuiltInServerOptions.getInstance().allowUnsignedRequests) {
    return EmptyHttpHeaders.INSTANCE
  }

  request.headers().get(HttpHeaderNames.COOKIE)?.let {
    for (cookie in ServerCookieDecoder.STRICT.decode(it)) {
      if (cookie.name() == STANDARD_COOKIE.name()) {
        if (cookie.value() == STANDARD_COOKIE.value()) {
          return EmptyHttpHeaders.INSTANCE
        }
        break
      }
    }
  }

  if (isSignedRequest) {
    return DefaultHttpHeaders().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(STANDARD_COOKIE) + "; SameSite=strict")
  }

  val urlDecoder = QueryStringDecoder(request.uri())
  if (!urlDecoder.path().endsWith("/favicon.ico")) {
    val url = "${channel.uriScheme}://${request.host!!}${urlDecoder.path()}"
    SwingUtilities.invokeAndWait {
      ProjectUtil.focusProjectWindow(null, true)

      if (MessageDialogBuilder
          .yesNo("", "Page '" + StringUtil.trimMiddle(url, 50) + "' requested without authorization, " +
              "\nyou can copy URL and open it in browser to trust it.")
          .icon(Messages.getWarningIcon())
          .yesText("Copy authorization URL to clipboard")
          .show() == Messages.YES) {
        CopyPasteManager.getInstance().setContents(StringSelection(url + "?" + TOKEN_PARAM_NAME + "=" + acquireToken()))
      }
    }
  }

  HttpResponseStatus.UNAUTHORIZED.orInSafeMode(HttpResponseStatus.NOT_FOUND).send(channel, request)
  return null
}

private fun toIdeaPath(decodedPath: String, offset: Int): String? {
  // must be absolute path (relative to DOCUMENT_ROOT, i.e. scheme://authority/) to properly canonicalize
  val path = decodedPath.substring(offset)
  if (!path.startsWith('/')) {
    return null
  }
  return FileUtil.toCanonicalPath(path, '/').substring(1)
}

fun compareNameAndProjectBasePath(projectName: String, project: Project): Boolean {
  val basePath = project.basePath
  return basePath != null && endsWithName(basePath, projectName)
}

fun findIndexFile(basedir: VirtualFile): VirtualFile? {
  val children = basedir.children
  if (children == null || children.isEmpty()) {
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

// is host loopback/any or network interface address (i.e. not custom domain)
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
    // WEB-8889
    // develar.local is own host name: develar. equals to "develar.labs.intellij.net" (canonical host name)
    return localHostName.equals(host, ignoreCase = true) || (host.endsWith(".local") && localHostName.regionMatches(0, host, 0, host.length - ".local".length, true))
  }
  catch (ignored: IOException) {
    return false
  }
}