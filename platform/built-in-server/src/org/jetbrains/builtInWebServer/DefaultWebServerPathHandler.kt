/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.endsWithName
import com.intellij.openapi.util.io.endsWithSlash
import com.intellij.openapi.util.io.getParentPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.isDirectory
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import org.jetbrains.io.*
import java.io.File
import java.nio.file.Paths
import java.util.regex.Pattern

private val chromeVersionFromUserAgent = Pattern.compile(" Chrome/([\\d.]+) ")

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
      pathInfo = pathToFileManager.doFindByRelativePath(path)
      if (pathInfo == null) {
        Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, null, request, extraHeaders)
        return true
      }

      pathToFileManager.pathToInfoCache.put(path, pathInfo)
    }

    var indexUsed = false
    if (pathInfo.isDirectory()) {
      var indexVirtualFile: VirtualFile? = null
      var indexFile: File? = null
      if (pathInfo.file == null) {
        indexFile = findIndexFile(pathInfo.ioFile!!)
      }
      else {
        indexVirtualFile = findIndexFile(pathInfo.file!!)
      }

      if (indexFile == null && indexVirtualFile == null) {
        Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, null, request, extraHeaders)
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
        Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request)
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
      LOG.catchAndLog {
        if (fileHandler.process(pathInfo!!, canonicalPath, project, request, channel, if (isCustomHost) null else projectName, extraHeaders)) {
          return true
        }
      }
    }

    // we registered as a last handler, so, we should just return 404 and send extra headers
    Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, null, request, extraHeaders)
    return true
  }
}

private fun checkAccess(pathInfo: PathInfo, channel: Channel, request: HttpRequest): Boolean {
  if (pathInfo.ioFile != null || pathInfo.file!!.isInLocalFileSystem) {
    val file = pathInfo.ioFile?.toPath() ?: Paths.get(pathInfo.file!!.path)
    if (file.isDirectory() || !hasAccess(file)) {
      Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request)
      return false
    }
  }
  else if (pathInfo.file!!.`is`(VFileProperty.HIDDEN)) {
    Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request)
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