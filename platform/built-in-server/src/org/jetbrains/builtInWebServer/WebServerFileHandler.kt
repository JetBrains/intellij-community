// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders

abstract class WebServerFileHandler {
  companion object {
    internal val EP_NAME = ExtensionPointName<WebServerFileHandler>("org.jetbrains.webServerFileHandler")
  }

  open val pageFileExtensions: Array<String>
    get() = emptyArray()

  /**
   * canonicalRequestPath contains index file name (if not specified in the request)
   */
  abstract fun process(pathInfo: PathInfo,
                       canonicalPath: CharSequence,
                       project: Project,
                       request: FullHttpRequest,
                       channel: Channel,
                       projectNameIfNotCustomHost: String?,
                       extraHeaders: HttpHeaders): Boolean
}

fun getRequestPath(canonicalPath: CharSequence, projectNameIfNotCustomHost: String?): String {
  return when (projectNameIfNotCustomHost) {
    null -> "/$canonicalPath"
    else -> "/$projectNameIfNotCustomHost/$canonicalPath"
  }
}