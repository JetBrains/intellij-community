// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer.ssi

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.io.lastModified
import com.intellij.util.io.size
import io.netty.handler.codec.http.HttpRequest
import org.jetbrains.builtInWebServer.RootProvider
import org.jetbrains.builtInWebServer.WebServerPathToFileManager
import java.nio.file.Path
import java.nio.file.Paths

@NlsSafe
private val VARIABLE_NAMES = arrayOf("AUTH_TYPE", "CONTENT_LENGTH", "CONTENT_TYPE", "DOCUMENT_NAME", "DOCUMENT_URI", "GATEWAY_INTERFACE",
  "HTTP_ACCEPT", "HTTP_ACCEPT_ENCODING", "HTTP_ACCEPT_LANGUAGE", "HTTP_CONNECTION", "HTTP_HOST", "HTTP_REFERER", "HTTP_USER_AGENT", "PATH_INFO",
  "PATH_TRANSLATED", "QUERY_STRING", "QUERY_STRING_UNESCAPED", "REMOTE_ADDR", "REMOTE_HOST", "REMOTE_PORT", "REMOTE_USER", "REQUEST_METHOD",
  "REQUEST_URI", "SCRIPT_FILENAME", "SCRIPT_NAME", "SERVER_ADDR", "SERVER_NAME", "SERVER_PORT", "SERVER_PROTOCOL", "SERVER_SOFTWARE", "UNIQUE_ID")

internal class SsiExternalResolver(private val project: Project,
                          private val request: HttpRequest,
                          private val parentPath: String,
                          private val parentFile: Path) {
  private val variables = HashMap<String, String>()

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

  fun findFileInProject(originalPath: String, virtual: Boolean): Path? {
    val path = findFile(originalPath, virtual)
    val underProjectRoot = runReadAction { ModuleManager.getInstance(project).modules }
      .filter { !it.isDisposed }
      .any { module ->
        RootProvider.values().asSequence()
          .flatMap { rootProvider -> rootProvider.getRoots(module.rootManager).asSequence() }
          .any { root -> FileUtil.isAncestor(root.path, path.toString(), false) }
      }

    return if (underProjectRoot) path else null
  }

  private fun findFile(originalPath: String, virtual: Boolean): Path? {
    var path = FileUtil.toCanonicalPath(originalPath, '/')
    if (!virtual) {
      return parentFile.resolve(path)
    }

    path = if (path[0] == '/') path else "$parentPath/$path"
    val pathInfo = WebServerPathToFileManager.getInstance(project).getPathInfo(path, true) ?: return null
    if (pathInfo.ioFile == null) {
      return Paths.get(pathInfo.file!!.path)
    }
    else {
      return pathInfo.ioFile!!
    }
  }

  fun getFileLastModified(path: String, virtual: Boolean): Long {
    val file = findFileInProject(path, virtual)
    return if (file == null || !file.exists()) 0 else file.lastModified().toMillis()
  }

  fun getFileSize(path: String, virtual: Boolean): Long {
    val file = findFileInProject(path, virtual)
    return if (file == null || !file.exists()) -1 else file.size()
  }
}