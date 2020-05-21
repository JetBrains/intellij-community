// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url

abstract class WebBrowserUrlProvider {
  /**
   * Browser exceptions are printed in Error Dialog when user presses any browser button
   */
  class BrowserException(message: String) : Exception(message)

  open fun canHandleElement(request: OpenInBrowserRequest): Boolean {
    val urls = try {
      getUrls(request)
    }
    catch (ignored: BrowserException) {
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
  open fun getUrls(request: OpenInBrowserRequest): Collection<Url> {
    return listOfNotNull(request.virtualFile?.let { getUrl(request, it) })
  }
}