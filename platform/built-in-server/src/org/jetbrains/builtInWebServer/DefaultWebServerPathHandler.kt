// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.endsWithName
import com.intellij.openapi.util.io.endsWithSlash
import com.intellij.openapi.util.io.getParentPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.io.*
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import org.jetbrains.io.orInSafeMode
import org.jetbrains.io.send
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

val chromeVersionFromUserAgent: Pattern = Pattern.compile(" Chrome/([\\d.]+) ")

private class DefaultWebServerPathHandler : WebServerPathHandler() {
  override fun process(path: String,
                       project: Project,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext,
                       projectName: String,
                       decodedRawPath: String,
                       isCustomHost: Boolean): Boolean {
    val channel = context.channel()

    val isSignedRequest = request.isSignedRequest()
    val extraHeaders = validateToken(request, channel, isSignedRequest) ?: return true

    val pathToFileManager = WebServerPathToFileManager.getInstance(project)
    var pathInfo = pathToFileManager.pathToInfoCache.getIfPresent(path)
    if (pathInfo == null || !pathInfo.isValid) {
      pathInfo = pathToFileManager.doFindByRelativePath(path, defaultPathQuery)
      if (pathInfo == null) {
        HttpResponseStatus.NOT_FOUND.send(channel, request, extraHeaders = extraHeaders)
        return true
      }

      pathToFileManager.pathToInfoCache.put(path, pathInfo)
    }

    var indexUsed = false
    if (pathInfo.isDirectory()) {
      var indexVirtualFile: VirtualFile? = null
      var indexFile: Path? = null
      if (pathInfo.file == null) {
        indexFile = findIndexFile(pathInfo.ioFile!!)
      }
      else {
        indexVirtualFile = findIndexFile(pathInfo.file!!)
      }

      if (indexFile == null && indexVirtualFile == null) {
        HttpResponseStatus.NOT_FOUND.send(channel, request, extraHeaders = extraHeaders)
        return true
      }

      // we must redirect only after index file check to not expose directory status
      if (!endsWithSlash(decodedRawPath)) {
        redirectToDirectory(request, channel, if (isCustomHost) path else "$projectName/$path", extraHeaders)
        return true
      }

      indexUsed = true
      pathInfo = PathInfo(indexFile, indexVirtualFile, pathInfo.root, pathInfo.moduleName, pathInfo.isLibrary)
      pathToFileManager.pathToInfoCache.put(path, pathInfo)
    }

    val userAgent = request.userAgent
    if (!isSignedRequest && userAgent != null && request.isRegularBrowser() && request.origin == null && request.referrer == null) {
      val matcher = chromeVersionFromUserAgent.matcher(userAgent)
      if (matcher.find() && StringUtil.compareVersionNumbers(matcher.group(1), "51") < 0 && !canBeAccessedDirectly(pathInfo.name)) {
        HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.NOT_FOUND).send(channel, request)
        return true
      }
    }

    if (!indexUsed && !endsWithName(path, pathInfo.name)) {
      if (endsWithSlash(decodedRawPath)) {
        indexUsed = true
      }
      else {
        // FallbackResource feature in action, /login requested, /index.php retrieved, we must not redirect /login to /login/
        val parentPath = getParentPath(pathInfo.path)
        if (parentPath != null && endsWithName(path, PathUtilRt.getFileName(parentPath))) {
          redirectToDirectory(request, channel, if (isCustomHost) path else "$projectName/$path", extraHeaders)
          return true
        }
      }
    }

    if (!checkAccess(pathInfo, channel, request)) {
      return true
    }

    val canonicalPath = if (indexUsed) "$path/${pathInfo.name}" else path
    for (fileHandler in WebServerFileHandler.EP_NAME.extensions) {
      LOG.runAndLogException {
        if (fileHandler.process(pathInfo, canonicalPath, project, request, channel, if (isCustomHost) null else projectName, extraHeaders)) {
          return true
        }
      }
    }

    // we registered as a last handler, so, we should just return 404 and send extra headers
    HttpResponseStatus.NOT_FOUND.send(channel, request, extraHeaders = extraHeaders)
    return true
  }
}

private fun checkAccess(pathInfo: PathInfo, channel: Channel, request: HttpRequest): Boolean {
  if (pathInfo.ioFile != null || pathInfo.file!!.isInLocalFileSystem) {
    val file = pathInfo.ioFile ?: Paths.get(pathInfo.file!!.path)
    if (file.isDirectory()) {
      HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.NOT_FOUND).send(channel, request)
      return false
    }
    else if (!hasAccess(file)) {
      // we check only file, but all directories in the path because of https://youtrack.jetbrains.com/issue/WEB-21594
      HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.NOT_FOUND).send(channel, request)
      return false
    }
  }
  else if (pathInfo.file!!.`is`(VFileProperty.HIDDEN)) {
    HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.NOT_FOUND).send(channel, request)
    return false
  }

  return true
}

private fun canBeAccessedDirectly(path: String): Boolean {
  for (fileHandler in WebServerFileHandler.EP_NAME.extensions) {
    for (ext in fileHandler.pageFileExtensions) {
      if (FileUtilRt.extensionEquals(path, ext)) {
        return true
      }
    }
  }
  return false
}