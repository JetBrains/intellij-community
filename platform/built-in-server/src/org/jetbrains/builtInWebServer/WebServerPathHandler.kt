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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import org.jetbrains.io.Responses
import org.jetbrains.io.host
import org.jetbrains.io.uriScheme

/**
 * By default [WebServerPathToFileManager] will be used to map request to file.
 * If file physically exists in the file system, you must use [WebServerRootsProvider].

 * Consider to extend [WebServerPathHandlerAdapter] instead of implement low-level [)][.process]
 */
abstract class WebServerPathHandler {
  companion object {
    internal val EP_NAME = ExtensionPointName.create<WebServerPathHandler>("org.jetbrains.webServerPathHandler")
  }

  abstract fun process(path: String,
                       project: Project,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext,
                       projectName: String,
                       decodedRawPath: String,
                       isCustomHost: Boolean): Boolean
}

fun redirectToDirectory(request: HttpRequest, channel: Channel, path: String) {
  val response = Responses.response(HttpResponseStatus.MOVED_PERMANENTLY)
  val url = VfsUtil.toUri("${channel.uriScheme}://${request.host}/$path/")!!
  response.headers().add(HttpHeaderNames.LOCATION, url.toASCIIString())
  Responses.send(response, channel, request)
}