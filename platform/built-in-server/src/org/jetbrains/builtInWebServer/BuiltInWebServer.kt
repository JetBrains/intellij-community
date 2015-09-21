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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.net.NetUtils
import io.netty.buffer.ByteBufUtf8Writer
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedStream
import org.jetbrains.builtInWebServer.ssi.SsiExternalResolver
import org.jetbrains.builtInWebServer.ssi.SsiProcessor
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses
import org.jetbrains.io.Responses
import org.jetbrains.io.Responses.addKeepAliveIfNeed
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException

public class BuiltInWebServer : HttpRequestHandler() {
  companion object {
    val LOG = Logger.getInstance(javaClass<BuiltInWebServer>())

    private fun doProcess(request: FullHttpRequest, context: ChannelHandlerContext, projectNameAsHost: String?): Boolean {
      val decodedPath = URLUtil.unescapePercentSequences(UriUtil.trimParameters(request.uri()))
      var offset: Int
      var emptyPath: Boolean
      val isCustomHost = projectNameAsHost != null
      var projectName: String
      if (isCustomHost) {
        projectName = projectNameAsHost!!
        // host mapped to us
        offset = 0
        emptyPath = decodedPath.isEmpty()
      }
      else {
        offset = decodedPath.indexOf('/', 1)
        projectName = decodedPath.substring(1, if (offset == -1) decodedPath.length() else offset)
        emptyPath = offset == -1
      }

      var candidateByDirectoryName: Project? = null
      val project = ProjectManager.getInstance().getOpenProjects().firstOrNull(fun(project: Project): Boolean {
        if (project.isDisposed()) {
          return false
        }

        val name = project.getName()
        if (isCustomHost) {
          // domain name is case-insensitive
          if (projectName.equals(project.getName(), ignoreCase = true)) {
            return true
          }
        }
        else {
          // WEB-17839 Internal web server reports 404 when serving files from project with slashes in name
          if (decodedPath.regionMatches(1, name, 0, name.length(), !SystemInfoRt.isFileSystemCaseSensitive)) {
            var emptyPathCandidate = decodedPath.length() == (name.length() + 1)
            if (emptyPathCandidate || decodedPath.charAt(name.length() + 1) == '/') {
              projectName = name
              offset = name.length() + 1
              emptyPath = emptyPathCandidate
              return true
            }
          }
        }

        if (candidateByDirectoryName == null && compareNameAndProjectBasePath(projectName, project)) {
          candidateByDirectoryName = project
        }
        return false
      }) ?: candidateByDirectoryName ?: return false

      if (emptyPath) {
        if (!SystemInfoRt.isFileSystemCaseSensitive) {
          // may be passed path is not correct
          projectName = project.getName()
        }

        // we must redirect "jsdebug" to "jsdebug/" as nginx does, otherwise browser will treat it as file instead of directory, so, relative path will not work
        WebServerPathHandler.redirectToDirectory(request, context.channel(), projectName)
        return true
      }

      val path = FileUtil.toCanonicalPath(decodedPath.substring(offset + 1), '/')
      for (pathHandler in WebServerPathHandler.EP_NAME.getExtensions()) {
        try {
          if (pathHandler.process(path, project, request, context, projectName, decodedPath, isCustomHost)) {
            return true
          }
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
      return false
    }
  }

  override fun isSupported(request: FullHttpRequest) = super.isSupported(request) || request.method() === HttpMethod.POST

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    var host = request.headers().getAsString(HttpHeaderNames.HOST)
    if (host.isNullOrEmpty()) {
      return false
    }

    val portIndex = host.indexOf(':')
    if (portIndex > 0) {
      host = host.substring(0, portIndex)
    }

    val projectName: String?
    val isIpv6 = host.charAt(0) == '[' && host.length() > 2 && host.charAt(host.length() - 1) == ']'
    if (isIpv6) {
      host = host.substring(1, host.length() - 1)
    }

    if (isIpv6 || InetAddresses.isInetAddress(host) || isOwnHostName(host) || host.endsWith(".ngrok.io")) {
      if (urlDecoder.path().length() < 2) {
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

public fun compareNameAndProjectBasePath(projectName: String, project: Project): Boolean {
  val basePath = project.getBasePath()
  return basePath != null && basePath.length() > projectName.length() && basePath.endsWith(projectName) && basePath.charAt(basePath.length() - projectName.length() - 1) == '/'
}

public fun findIndexFile(basedir: VirtualFile): VirtualFile? {
  val children = basedir.getChildren()
  if (children == null || children.isEmpty()) {
    return null
  }

  for (indexNamePrefix in arrayOf("index.", "default.")) {
    var index: VirtualFile? = null
    val preferredName = indexNamePrefix + "html"
    for (child in children) {
      if (!child.isDirectory()) {
        val name = child.getName()
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

public fun isOwnHostName(host: String): Boolean {
  if (NetUtils.isLocalhost(host)) {
    return true
  }

  try {
    val address = InetAddress.getByName(host)
    if (host == address.getHostAddress() || host.equals(address.getCanonicalHostName(), ignoreCase = true)) {
      return true
    }

    val localHostName = InetAddress.getLocalHost().getHostName()
    // WEB-8889
    // develar.local is own host name: develar. equals to "develar.labs.intellij.net" (canonical host name)
    return localHostName.equals(host, ignoreCase = true) || (host.endsWith(".local") && localHostName.regionMatches(0, host, 0, host.length() - ".local".length(), true))
  }
  catch (ignored: UnknownHostException) {
    return false
  }
}

private class StaticFileHandler : WebServerFileHandler() {
  private var ssiProcessor: SsiProcessor? = null

  override fun process(file: VirtualFile, canonicalRequestPath: CharSequence, project: Project, request: FullHttpRequest, channel: Channel, isCustomHost: Boolean): Boolean {
    if (file.isInLocalFileSystem()) {
      val nameSequence = file.getNameSequence()
      //noinspection SpellCheckingInspection
      if (StringUtilRt.endsWithIgnoreCase(nameSequence, ".shtml") || StringUtilRt.endsWithIgnoreCase(nameSequence, ".stm") || StringUtilRt.endsWithIgnoreCase(nameSequence, ".shtm")) {
        processSsi(file, canonicalRequestPath, project, request, channel, isCustomHost)
        return true
      }

      val ioFile = VfsUtilCore.virtualToIoFile(file)
      if (hasAccess(ioFile)) {
        FileResponses.sendFile(request, channel, ioFile)
      }
      else {
        Responses.sendStatus(HttpResponseStatus.FORBIDDEN, channel, request)
      }
    }
    else {
      val response = FileResponses.prepareSend(request, channel, file.getTimeStamp(), file.getPath()) ?: return true

      val keepAlive = addKeepAliveIfNeed(response, request)
      if (request.method() !== HttpMethod.HEAD) {
        HttpUtil.setContentLength(response, file.getLength())
      }

      channel.write(response)

      if (request.method() != HttpMethod.HEAD) {
        channel.write(ChunkedStream(file.getInputStream()))
      }

      val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
      if (!keepAlive) {
        future.addListener(ChannelFutureListener.CLOSE)
      }
    }
    return true
  }

  private fun processSsi(file: VirtualFile, canonicalRequestPath: CharSequence, project: Project, request: FullHttpRequest, channel: Channel, isCustomHost: Boolean) {
    var path = PathUtilRt.getParentPath(canonicalRequestPath.toString())
    if (!isCustomHost) {
      // remove project name - SSI resolves files only inside current project
      path = path.substring(path.indexOf('/', 1) + 1)
    }

    if (ssiProcessor == null) {
      ssiProcessor = SsiProcessor(false)
    }

    val buffer = channel.alloc().ioBuffer()
    val keepAlive: Boolean
    var releaseBuffer = true
    try {
      val lastModified = ssiProcessor!!.process(SsiExternalResolver(project, request, path, file.getParent()), VfsUtilCore.loadText(file), file.getTimeStamp(), ByteBufUtf8Writer(buffer))

      val response = FileResponses.prepareSend(request, channel, lastModified, file.getPath()) ?: return

      keepAlive = addKeepAliveIfNeed(response, request)
      if (request.method() !== HttpMethod.HEAD) {
        HttpUtil.setContentLength(response, buffer.readableBytes().toLong())
      }

      channel.write(response)

      if (request.method() !== HttpMethod.HEAD) {
        releaseBuffer = false
        channel.write(buffer)
      }
    }
    finally {
      if (releaseBuffer) {
        buffer.release()
      }
    }

    val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE)
    }
  }

  // deny access to .htaccess files
  private fun hasAccess(result: File) = !result.isDirectory() && result.canRead() && !(result.isHidden() || result.getName().startsWith(".ht"))
}