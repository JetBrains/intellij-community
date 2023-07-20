// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.net.URL

private typealias CefResourceProvider = () -> CefResourceHandler?

class CefLocalRequestHandler(
  private val myProtocol: String,
  private val myAuthority: String
) : CefRequestHandlerAdapter() {
  private val myResources: MutableMap<String, CefResourceProvider> = HashMap()

  private val REJECTING_RESOURCE_HANDLER: CefResourceHandler = object : CefResourceHandlerAdapter() {
    override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
      callback.cancel()
      return false
    }
  }

  private val RESOURCE_REQUEST_HANDLER = object : CefResourceRequestHandlerAdapter() {
    override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler {
      val url = URL(request.url)
      url.protocol
      if (!url.protocol.equals(myProtocol) || !url.authority.equals(myAuthority)) {
        return REJECTING_RESOURCE_HANDLER
      }
      return try {
        myResources[url.path]?.let { it() } ?: REJECTING_RESOURCE_HANDLER
      } catch (e: RuntimeException) {
        REJECTING_RESOURCE_HANDLER
      }
    }
  }

  fun addResource(resourcePath: String, resourceProvider: CefResourceProvider) {
    myResources[resourcePath] = resourceProvider
  }

  override fun getResourceRequestHandler(browser: CefBrowser?,
                                         frame: CefFrame?,
                                         request: CefRequest?,
                                         isNavigation: Boolean,
                                         isDownload: Boolean,
                                         requestInitiator: String?,
                                         disableDefaultHandling: BoolRef?): CefResourceRequestHandler {
    return RESOURCE_REQUEST_HANDLER
  }
}
