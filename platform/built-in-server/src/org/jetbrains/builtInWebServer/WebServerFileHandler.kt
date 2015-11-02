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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest

abstract class WebServerFileHandler {
  companion object {
    internal val EP_NAME = ExtensionPointName.create<WebServerFileHandler>("org.jetbrains.webServerFileHandler")
  }

  /**
   * canonicalRequestPath contains index file name (if not specified in the request)
   */
  abstract fun process(pathInfo: PathInfo,
                       canonicalPath: CharSequence,
                       project: Project,
                       request: FullHttpRequest,
                       channel: Channel,
                       projectNameIfNotCustomHost: String?): Boolean

}

fun getRequestPath(canonicalPath: CharSequence, projectNameIfNotCustomHost: String?) = if (projectNameIfNotCustomHost == null) "/$canonicalPath" else "/$projectNameIfNotCustomHost/$canonicalPath"