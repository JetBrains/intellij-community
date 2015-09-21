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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import org.jetbrains.io.Responses

class DefaultWebServerPathHandler : WebServerPathHandler() {
  override fun process(path: String,
                       project: Project,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext,
                       projectName: String?,
                       decodedRawPath: String,
                       isCustomHost: Boolean): Boolean {
    val channel = context.channel()
    val pathToFileManager = WebServerPathToFileManager.getInstance(project)
    var result: VirtualFile? = pathToFileManager.pathToFileCache.getIfPresent(path)
    var indexUsed = false
    if (result == null || !result.isValid) {
      result = pathToFileManager.findByRelativePath(project, path)
      if (result == null) {
        if (path.isEmpty()) {
          Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, "Index file doesn't exist.", request)
          return true
        }
        else {
          return false
        }
      }
      else if (result.isDirectory) {
        if (!WebServerPathHandler.endsWithSlash(decodedRawPath)) {
          WebServerPathHandler.redirectToDirectory(request, channel, if (isCustomHost) path else (projectName + '/' + path))
          return true
        }

        result = findIndexFile(result)
        if (result == null) {
          Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, "Index file doesn't exist.", request)
          return true
        }
        indexUsed = true
      }

      pathToFileManager.pathToFileCache.put(path, result)
    }
    else if (!path.endsWith(result.name)) {
      if (WebServerPathHandler.endsWithSlash(decodedRawPath)) {
        indexUsed = true
      }
      else {
        // FallbackResource feature in action, /login requested, /index.php retrieved, we must not redirect /login to /login/
        if (path.endsWith(result.parent.name)) {
          WebServerPathHandler.redirectToDirectory(request, channel, if (isCustomHost) path else (projectName + '/' + path))
          return true
        }
      }
    }

    val canonicalRequestPath = StringBuilder()
    canonicalRequestPath.append('/')
    if (!isCustomHost) {
      canonicalRequestPath.append(projectName).append('/')
    }
    canonicalRequestPath.append(path)
    if (indexUsed) {
      canonicalRequestPath.append('/').append(result.name)
    }

    for (fileHandler in WebServerFileHandler.EP_NAME.extensions) {
      try {
        if (fileHandler.process(result, canonicalRequestPath, project, request, channel, isCustomHost)) {
          return true
        }
      }
      catch (e: Throwable) {
        BuiltInWebServer.LOG.error(e)
      }
    }
    return false
  }
}