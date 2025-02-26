// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.endsWithName
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.orInSafeMode
import org.jetbrains.io.send
import java.nio.file.Path

private class DefaultWebServerPathHandler : WebServerPathHandler {
  private val FILE_HANDLER_EP_NAME = ExtensionPointName<WebServerFileHandler>("org.jetbrains.webServerFileHandler")

  override fun process(
    path: String,
    project: Project,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
    projectName: String,
    authHeaders: HttpHeaders,
    isCustomHost: Boolean,
  ): Boolean {
    val channel = context.channel()
    val decodedRawPath = QueryStringDecoder(request.uri()).path()

    val pathToFileManager = WebServerPathToFileManager.getInstance(project)
    var pathInfo = pathToFileManager.pathToInfoCache.getIfPresent(path)
    if (pathInfo == null || !pathInfo.isValid) {
      pathInfo = pathToFileManager.doFindByRelativePath(path, defaultPathQuery)
      if (pathInfo == null) {
        HttpResponseStatus.NOT_FOUND.send(channel, request, extraHeaders = authHeaders)
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
        HttpResponseStatus.NOT_FOUND.send(channel, request, extraHeaders = authHeaders)
        return true
      }

      // we must redirect only after index file check to not expose directory status
      if (!decodedRawPath.endsWith('/')) {
        redirectToDirectory(request, channel, extraHeaders = authHeaders)
        return true
      }

      indexUsed = true
      pathInfo = PathInfo(indexFile, indexVirtualFile, pathInfo.root, pathInfo.moduleName, pathInfo.isLibrary)
      pathToFileManager.pathToInfoCache.put(path, pathInfo)
    }

    if (!indexUsed && !endsWithName(path, pathInfo.name)) {
      if (decodedRawPath.endsWith('/')) {
        indexUsed = true
      }
      else {
        // FallbackResource feature in action, /login requested, /index.php retrieved, we must not redirect /login to /login/
        val parentPath = PathUtilRt.getParentPath(pathInfo.path).takeIf { it.isNotEmpty() }
        if (parentPath != null && endsWithName(path, PathUtilRt.getFileName(parentPath))) {
          redirectToDirectory(request, channel, extraHeaders = authHeaders)
          return true
        }
      }
    }

    if (!checkAccess(pathInfo, project)) {
      HttpResponseStatus.FORBIDDEN.orInSafeMode(HttpResponseStatus.NOT_FOUND).send(channel, request, extraHeaders = authHeaders)
      return true
    }

    val canonicalPath = if (indexUsed) "${path}/${pathInfo.name}" else path
    for (fileHandler in FILE_HANDLER_EP_NAME.extensionList) {
      LOG.runAndLogException {
        if (fileHandler.process(pathInfo, canonicalPath, project, request, channel, if (isCustomHost) null else projectName, authHeaders)) {
          return true
        }
      }
    }

    return false
  }

  private fun checkAccess(pathInfo: PathInfo, project: Project): Boolean = when {
    pathInfo.ioFile != null -> checkAccess(pathInfo.ioFile!!, project)
    pathInfo.file!!.isInLocalFileSystem -> checkAccess(pathInfo.file!!.toNioPath(), project)
    pathInfo.file!!.`is`(VFileProperty.HIDDEN) -> false
    else -> true
  }

  private fun checkAccess(file: Path, project: Project): Boolean =
    hasAccess(file) &&
    project.isTrusted() || runCatching { file.toRealPath().startsWith(project.basePath!!) }.getOrDefault(false)
}
