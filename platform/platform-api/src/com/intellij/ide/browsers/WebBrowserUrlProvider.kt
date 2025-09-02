// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url

abstract class WebBrowserUrlProvider : PossiblyDumbAware {
  /**
   * Browser exceptions are printed in the Error Dialog when a user presses any browser button.
   */
  class BrowserException(message: String) : Exception(message)

  open fun canHandleElement(request: OpenInBrowserRequest): Boolean {
    val urls = try {
      getUrls(request)
    }
    catch (_: BrowserException) {
      return false
    }

    if (!urls.isEmpty()) {
      request.result = urls
      return true
    }

    return false
  }

  @Throws(BrowserException::class)
  protected open fun getUrl(request: OpenInBrowserRequest, file: VirtualFile): Url? = null

  @Throws(BrowserException::class)
  open fun getUrls(request: OpenInBrowserRequest): Collection<Url> =
    listOfNotNull(request.virtualFile?.let { getUrl(request, it) })
}
