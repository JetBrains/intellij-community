// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.intellij.ide.browsers.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
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

    // we must use base language because we serve file - not part of file, but the entire Handlebars file, for example,
    // contains HTML and HBS trees, so regardless of context, we should not handle such a file
    return request.isPhysicalFile() && isFileOfMyLanguage(request.file)
  }

  protected open fun isFileOfMyLanguage(psiFile: PsiFile): Boolean = WebBrowserXmlService.getInstance().isHtmlOrXmlFile(psiFile)

  override fun getUrl(request: OpenInBrowserRequest, file: VirtualFile): Url? =
    if (file is HttpVirtualFile) Urls.newFromVirtualFile(file)
    else getBuiltInServerUrls(file, request.project, null, request.isAppendAccessToken, request.reloadMode).firstOrNull()
}

@JvmOverloads
fun getBuiltInServerUrls(
  file: VirtualFile,
  project: Project,
  currentAuthority: String?,
  appendAccessToken: Boolean = true,
  reloadMode: ReloadMode? = null,
): List<Url> {
  if (currentAuthority != null && !compareAuthority(currentAuthority)) {
    return emptyList()
  }

  val info = WebServerPathToFileManager.getInstance(project).getPathInfo(file) ?: return emptyList()
  val effectivePort = BuiltInServerOptions.getInstance().effectiveBuiltInServerPort
  val path = info.path
  val authority = currentAuthority ?: "localhost:${effectivePort}"
  @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
  val reloadMode = reloadMode ?: WebBrowserManager.getInstance().webServerReloadMode
  val appendReloadOnSave = reloadMode != ReloadMode.DISABLED
  val queryBuilder = StringBuilder()
  if (appendAccessToken || appendReloadOnSave) queryBuilder.append('?')
  if (appendAccessToken) queryBuilder.append(TOKEN_PARAM_NAME).append('=').append(service<BuiltInWebServerAuth>().acquireToken())
  if (appendAccessToken && appendReloadOnSave) queryBuilder.append('&')
  if (appendReloadOnSave) queryBuilder.append(WebServerPageConnectionService.RELOAD_URL_PARAM).append('=').append(reloadMode.name)
  val query = queryBuilder.toString()

  val urls = SmartList(Urls.newHttpUrl(authority, "/${project.name}/${path}", query))

  val path2 = info.rootLessPathIfPossible
  if (path2 != null) {
    urls += Urls.newHttpUrl(authority, "/${project.name}/${path2}", query)
  }

  val defaultPort = BuiltInServerManager.getInstance().port
  if (currentAuthority == null && defaultPort != effectivePort) {
    val defaultAuthority = "localhost:$defaultPort"
    urls += Urls.newHttpUrl(defaultAuthority, "/${project.name}/${path}", query)
    if (path2 != null) {
      urls += Urls.newHttpUrl(defaultAuthority, "/${project.name}/${path2}", query)
    }
  }

  return urls
}

fun compareAuthority(currentAuthority: String?): Boolean {
  if (currentAuthority.isNullOrEmpty()) {
    return false
  }

  val portIndex = currentAuthority.indexOf(':')
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
