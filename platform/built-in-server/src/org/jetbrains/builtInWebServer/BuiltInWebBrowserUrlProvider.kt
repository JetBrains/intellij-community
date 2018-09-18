// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.BuiltinWebServerAccess
import com.intellij.util.Url
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager
import java.io.IOException
import java.util.*

open class BuiltInWebBrowserUrlProvider : WebBrowserUrlProvider(), DumbAware {
  override fun canHandleElement(request: OpenInBrowserRequest): Boolean {
    if (request.virtualFile is HttpVirtualFile) {
      return true
    }

    // we must use base language because we serve file - not part of file, but the whole file
    // handlebars, for example, contains HTML and HBS psi trees, so, regardless of context, we should not handle such file
    val viewProvider = request.file.viewProvider
    return request.isPhysicalFile() && isFileOfMyLanguage(request.file)
  }

  protected open fun isFileOfMyLanguage(psiFile: PsiFile): Boolean = WebBrowserService.isHtmlOrXmlFile(psiFile)

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

  val path = WebServerPathToFileManager.getInstance(project).getPath(file) ?: return emptyList()

  val effectiveBuiltInServerPort = BuiltInServerOptions.getInstance().effectiveBuiltInServerPort
  var userToken: String? = null
  try {
    userToken = BuiltinWebServerAccess.getUserAuthenticationToken()
  }
  catch (e: IOException) {
    LOG.warn(String.format("Unable to get User authentication token for launching path '%s'", path), e)
    return emptyList()
  }

  val url = Urls.newHttpUrl(currentAuthority ?: "localhost:" + effectiveBuiltInServerPort,
      '/' + userToken + '/' + project.name + '/' + path)
  val defaultPort = BuiltInServerManager.getInstance().port
  if (currentAuthority != null || defaultPort == effectiveBuiltInServerPort) {
    return listOf(url)
  }
  return Arrays.asList(url, Urls.newHttpUrl(currentAuthority ?: "localhost:" + defaultPort,
      '/' + userToken + '/' + project.name + '/' + path))

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