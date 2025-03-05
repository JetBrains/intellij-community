// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders

abstract class WebServerFileHandler {
  open val pageFileExtensions: List<String>
    get() = emptyList()

  /**
   * `canonicalRequestPath` contains index file name (if not specified in the request)
   */
  abstract fun process(
    pathInfo: PathInfo,
    canonicalPath: CharSequence,
    project: Project,
    request: FullHttpRequest,
    channel: Channel,
    projectNameIfNotCustomHost: String?,
    extraHeaders: HttpHeaders,
  ): Boolean

  protected fun getRequestPath(canonicalPath: CharSequence, projectNameIfNotCustomHost: String?): String =
    if (projectNameIfNotCustomHost == null) "/${canonicalPath}"
    else "/${projectNameIfNotCustomHost}/${canonicalPath}"
}
