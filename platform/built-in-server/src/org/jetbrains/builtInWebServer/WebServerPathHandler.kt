// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.host
import com.intellij.util.io.uriScheme
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.io.response
import org.jetbrains.io.send

/**
 * By default [WebServerPathToFileManager] will be used to map request to file.
 * If file physically exists in the file system, you must use [WebServerRootsProvider].

 * Consider extending [WebServerPathHandlerAdapter] instead of implement low-level [process].
 */
abstract class WebServerPathHandler {
  abstract fun process(path: String,
                       project: Project,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext,
                       projectName: String,
                       decodedRawPath: String,
                       isCustomHost: Boolean): Boolean
}

internal fun redirectToDirectory(request: HttpRequest, channel: Channel, path: String, extraHeaders: HttpHeaders?) {
  val response = HttpResponseStatus.MOVED_PERMANENTLY.response(request)
  val url = VfsUtil.toUri("${channel.uriScheme}://${request.host!!}/$path/")!!
  response.headers().add(HttpHeaderNames.LOCATION, url.toASCIIString())
  response.send(channel, request, extraHeaders)
}

abstract class WebServerPathHandlerAdapter : WebServerPathHandler() {
  protected abstract fun process(path: String, project: Project, request: FullHttpRequest, context: ChannelHandlerContext): Boolean
  override fun process(path: String,
                       project: Project,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext,
                       projectName: String,
                       decodedRawPath: String,
                       isCustomHost: Boolean): Boolean {
    return process(path, project, request, context)
  }
}