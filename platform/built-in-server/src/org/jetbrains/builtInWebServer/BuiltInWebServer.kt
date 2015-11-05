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

import com.google.common.net.InetAddresses
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.endsWithName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.net.NetUtils
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.host
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException

internal val LOG = Logger.getInstance(BuiltInWebServer::class.java)

class BuiltInWebServer : HttpRequestHandler() {
  override fun isSupported(request: FullHttpRequest) = super.isSupported(request) || request.method() == HttpMethod.POST

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    var host = request.host
    if (host.isNullOrEmpty()) {
      return false
    }

    val portIndex = host.indexOf(':')
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
      projectName = host
    }
    return doProcess(request, context, projectName)
  }
}

private fun doProcess(request: FullHttpRequest, context: ChannelHandlerContext, projectNameAsHost: String?): Boolean {
  val decodedPath = URLUtil.unescapePercentSequences(UriUtil.trimParameters(request.uri()))
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
        var isEmptyPathCandidate = decodedPath.length == (name.length + 1)
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

  if (isEmptyPath) {
    // we must redirect "jsdebug" to "jsdebug/" as nginx does, otherwise browser will treat it as a file instead of a directory, so, relative path will not work
    redirectToDirectory(request, context.channel(), projectName)
    return true
  }

  val path = FileUtil.toCanonicalPath(decodedPath.substring(offset + 1), '/')
  for (pathHandler in WebServerPathHandler.EP_NAME.extensions) {
    LOG.catchAndLog {
      if (pathHandler.process(path, project, request, context, projectName, decodedPath, isCustomHost)) {
        return true
      }
    }
  }
  return false
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

fun findIndexFile(basedir: File): File? {
  val children = basedir.listFiles { dir, name -> name.startsWith("index.") || name.startsWith("default.") }
  if (children == null || children.isEmpty()) {
    return null
  }

  for (indexNamePrefix in arrayOf("index.", "default.")) {
    var index: File? = null
    val preferredName = "${indexNamePrefix}html"
    for (child in children) {
      if (!child.isDirectory) {
        val name = child.name
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

fun isOwnHostName(host: String): Boolean {
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
  catch (ignored: UnknownHostException) {
    return false
  }
}