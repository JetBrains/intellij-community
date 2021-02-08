// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.builtInWebServer

import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserUrlProvider
import com.intellij.ide.browsers.WebBrowserXmlService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.builtInWebServer.liveReload.WebServerPageConnectionService
import org.jetbrains.ide.BuiltInServerManager

open class BuiltInWebBrowserUrlProvider : WebBrowserUrlProvider(), DumbAware {
  override fun canHandleElement(request: OpenInBrowserRequest): Boolean {
    if (request.virtualFile is HttpVirtualFile) {
      return true
    }

    // we must use base language because we serve file - not part of file, but the whole file
    // handlebars, for example, contains HTML and HBS psi trees, so, regardless of context, we should not handle such file
    return request.isPhysicalFile() && isFileOfMyLanguage(request.file)
  }

  protected open fun isFileOfMyLanguage(psiFile: PsiFile): Boolean = WebBrowserXmlService.getInstance().isHtmlOrXmlFile(psiFile)

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
  return getBuiltInServerUrls(info, project, currentAuthority, appendAccessToken)
}

fun getBuiltInServerUrls(info: PathInfo, project: Project, currentAuthority: String? = null, appendAccessToken: Boolean = true): SmartList<Url> {
  val effectiveBuiltInServerPort = BuiltInServerOptions.getInstance().effectiveBuiltInServerPort
  val path = info.path

  val authority = currentAuthority ?: "localhost:$effectiveBuiltInServerPort"
  val appendReloadOnSave = Registry.get("ide.built.in.web.server.reload.on.save").asBoolean()
  val queryBuilder = StringBuilder()
  if (appendAccessToken || appendReloadOnSave) queryBuilder.append('?')
  if (appendAccessToken) queryBuilder.append(TOKEN_PARAM_NAME).append('=').append(acquireToken())
  if (appendAccessToken && appendReloadOnSave) queryBuilder.append('&')
  if (appendReloadOnSave) queryBuilder.append(WebServerPageConnectionService.RELOAD_URL_PARAM)
  val query = queryBuilder.toString()

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