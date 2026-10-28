// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a browser that a plugin implements, such as the IDE Browser.
 *
 * The browser is not in the list of [WebBrowserManager]. The "Default Browser" dropdown offers it after "System default".
 * When it is the default browser, every link without a browser opens in it with [WebBrowser.browse].
 */
@ApiStatus.Internal
interface WebBrowserProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<WebBrowserProvider> = ExtensionPointName("com.intellij.webBrowserProvider")

    /**
     * Returns the browsers that can open pages now.
     */
    @JvmStatic
    fun getAvailableBrowsers(): List<WebBrowser> = EP_NAME.extensionList.mapNotNull { it.browser }
  }

  /**
   * The browser, or `null` while it cannot open pages, for example when JCEF is not supported.
   * The id of the browser must not change, because the settings store it.
   */
  val browser: WebBrowser?
}
