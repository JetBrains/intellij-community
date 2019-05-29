/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

 * Consider to extend [WebServerPathHandlerAdapter] instead of implement low-level [process].
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

internal fun redirectToDirectory(request: HttpRequest, channel: Channel, path: String, extraHeaders: HttpHeaders?) {
  val response = HttpResponseStatus.MOVED_PERMANENTLY.response(request)
  val url = VfsUtil.toUri("${channel.uriScheme}://${request.host!!}/$path/")!!
  response.headers().add(HttpHeaderNames.LOCATION, url.toASCIIString())
  response.send(channel, request, extraHeaders)
}