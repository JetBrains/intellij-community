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

import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.ide.browsers.WebBrowserUrlProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.SmartList
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager

open class BuiltInWebBrowserUrlProvider : WebBrowserUrlProvider(), DumbAware {
  override fun canHandleElement(request: OpenInBrowserRequest): Boolean {
    if (request.virtualFile is HttpVirtualFile) {
      return true
    }

    // we must use base language because we serve file - not part of file, but the whole file
    // handlebars, for example, contains HTML and HBS psi trees, so, regardless of context, we should not handle such file
    val viewProvider = request.file.viewProvider
    return viewProvider.isPhysical && request.virtualFile !is LightVirtualFile && isFileOfMyLanguage(request.file)
  }

  protected open fun isFileOfMyLanguage(psiFile: PsiFile) = WebBrowserService.isHtmlOrXmlFile(psiFile)

  override fun getUrl(request: OpenInBrowserRequest, file: VirtualFile): Url? {
    if (file is HttpVirtualFile) {
      return Urls.newFromVirtualFile(file)
    }
    else {
      return getBuiltInServerUrls(file, request.project, null, request.isAppendAccessToken).firstOrNull()
    }
  }
}

@JvmOverloads
fun getBuiltInServerUrls(file: VirtualFile,
                                       project: Project,
                                       currentAuthority: String?,
                                       appendAccessToken: Boolean = true): List<Url> {
  if (currentAuthority != null && !compareAuthority(currentAuthority)) {
    return emptyList()
  }

  val info = WebServerPathToFileManager.getInstance(project).getPathInfo(file) ?: return emptyList()

  val effectiveBuiltInServerPort = BuiltInServerOptions.getInstance().effectiveBuiltInServerPort
  val path = info.path

  val authority = currentAuthority ?: "localhost:$effectiveBuiltInServerPort"
  val query = if (appendAccessToken) "?$TOKEN_PARAM_NAME=${acquireToken()}" else ""
  val urls = SmartList(Urls.newHttpUrl(authority, "/${project.name}/$path", query))

  val path2 = info.rootLessPathIfPossible
  if (path2 != null) {
    urls.add(Urls.newHttpUrl(authority, '/' + project.name + '/' + path2, query))
  }

  val defaultPort = BuiltInServerManager.getInstance().port
  if (currentAuthority == null && defaultPort != effectiveBuiltInServerPort) {
    val defaultAuthority = "localhost:$defaultPort"
    urls.add(Urls.newHttpUrl(defaultAuthority, "/${project.name}/$path", query))
    if (path2 != null) {
      urls.add(Urls.newHttpUrl(defaultAuthority, "/${project.name}/$path2", query))
    }
  }

  return urls
}

fun compareAuthority(currentAuthority: String?): Boolean {
  if (currentAuthority.isNullOrEmpty()) {
    return false
  }

  val portIndex = currentAuthority!!.indexOf(':')
  if (portIndex < 0) {
    return false
  }

  val host = currentAuthority.substring(0, portIndex)
  if (!isOwnHostName(host)) {
    return false
  }

  val port = StringUtil.parseInt(currentAuthority.substring(portIndex + 1), -1)
  return port == BuiltInServerOptions.getInstance().effectiveBuiltInServerPort || port == BuiltInServerManager.getInstance().port
}