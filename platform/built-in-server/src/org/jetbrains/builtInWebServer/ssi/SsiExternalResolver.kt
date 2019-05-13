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
package org.jetbrains.builtInWebServer.ssi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.io.lastModified
import com.intellij.util.io.size
import gnu.trove.THashMap
import io.netty.handler.codec.http.HttpRequest
import org.jetbrains.builtInWebServer.WebServerPathToFileManager
import java.nio.file.Path
import java.nio.file.Paths

private val VARIABLE_NAMES = arrayOf("AUTH_TYPE", "CONTENT_LENGTH", "CONTENT_TYPE", "DOCUMENT_NAME", "DOCUMENT_URI", "GATEWAY_INTERFACE",
  "HTTP_ACCEPT", "HTTP_ACCEPT_ENCODING", "HTTP_ACCEPT_LANGUAGE", "HTTP_CONNECTION", "HTTP_HOST", "HTTP_REFERER", "HTTP_USER_AGENT", "PATH_INFO",
  "PATH_TRANSLATED", "QUERY_STRING", "QUERY_STRING_UNESCAPED", "REMOTE_ADDR", "REMOTE_HOST", "REMOTE_PORT", "REMOTE_USER", "REQUEST_METHOD",
  "REQUEST_URI", "SCRIPT_FILENAME", "SCRIPT_NAME", "SERVER_ADDR", "SERVER_NAME", "SERVER_PORT", "SERVER_PROTOCOL", "SERVER_SOFTWARE", "UNIQUE_ID")

class SsiExternalResolver(private val project: Project,
                          private val request: HttpRequest,
                          private val parentPath: String,
                          private val parentFile: Path) {
  private val variables = THashMap<String, String>()

  fun addVariableNames(variableNames: MutableCollection<String>) {
    for (variableName in VARIABLE_NAMES) {
      val variableValue = getVariableValue(variableName)
      if (variableValue != null) {
        variableNames.add(variableName)
      }
    }
  }

  fun setVariableValue(name: String, value: String) {
    variables.put(name, value)
  }

  fun getVariableValue(name: String): String? {
    val value = variables[name]
    return value ?: request.headers().get(name)
  }

  fun findFile(originalPath: String, virtual: Boolean): Path? {
    var path = FileUtil.toCanonicalPath(originalPath, '/')
    if (!virtual) {
      return parentFile.resolve(path)
    }

    path = if (path[0] == '/') path else parentPath + '/' + path
    val pathInfo = WebServerPathToFileManager.getInstance(project).getPathInfo(path, true) ?: return null
    if (pathInfo.ioFile == null) {
      return Paths.get(pathInfo.file!!.path)
    }
    else {
      return pathInfo.ioFile!!
    }
  }

  fun getFileLastModified(path: String, virtual: Boolean): Long {
    val file = findFile(path, virtual)
    return if (file == null || !file.exists()) 0 else file.lastModified().toMillis()
  }

  fun getFileSize(path: String, virtual: Boolean): Long {
    val file = findFile(path, virtual)
    return if (file == null || !file.exists()) -1 else file.size()
  }
}