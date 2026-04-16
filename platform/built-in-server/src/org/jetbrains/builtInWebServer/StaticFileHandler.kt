// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.stream.ChunkedStream
import org.jetbrains.builtInWebServer.liveReload.WebServerPageConnectionService
import org.jetbrains.io.FileResponses
import org.jetbrains.io.addKeepAliveIfNeeded
import org.jetbrains.io.flushChunkedResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private class StaticFileHandler : WebServerFileHandler() {
  override val pageFileExtensions = listOf("html", "htm", "shtml", "stm", "shtm")

  override fun process(pathInfo: PathInfo, canonicalPath: CharSequence, project: Project, request: FullHttpRequest, channel: Channel, projectNameIfNotCustomHost: String?, extraHeaders: HttpHeaders): Boolean {
    if (pathInfo.ioFile != null || pathInfo.file!!.isInLocalFileSystem) {
      val ioFile = pathInfo.ioFile ?: Paths.get(pathInfo.file!!.path)

      val extraSuffix = WebServerPageConnectionService.instance.fileRequested(request, true, pathInfo::getOrResolveVirtualFile)
      val extraBuffer = extraSuffix?.toByteArray(pathInfo.file?.charset ?: Charsets.UTF_8)
      FileResponses.sendFile(request, channel, ioFile, extraHeaders, extraBuffer)
      return true
    }

    val file = pathInfo.file!!
    val response = FileResponses.prepareSend(request, channel, file.timeStamp, file.name, extraHeaders) ?: return true

    val isKeepAlive = response.addKeepAliveIfNeeded(request)
    if (request.method() != HttpMethod.HEAD) {
      HttpUtil.setContentLength(response, file.length)
    }

    channel.write(response)

    if (request.method() != HttpMethod.HEAD) {
      channel.write(ChunkedStream(file.inputStream))
    }

    flushChunkedResponse(channel, isKeepAlive)
    return true
  }
}

fun checkAccess(file: Path, root: Path = file.root): Boolean {
  var parent = file
  do {
    if (!hasAccess(parent)) {
      return false
    }
    parent = parent.parent ?: break
  }
  while (parent != root)
  return true
}

// deny access to any dot prefixed file
internal fun hasAccess(result: Path) = Files.isReadable(result) && !(Files.isHidden(result) || result.fileName.toString().startsWith('.'))