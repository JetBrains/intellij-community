// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * A web search engine. [WebBrowserManager.getSearchEngine] gives the engine that the developer picked
 * on the *Settings | Tools | Web Browsers and Preview* page.
 * The IDE Browser searches with it.
 */
@ApiStatus.Internal
enum class SearchEngine(
  val displayName: @NlsSafe String,
  private val searchUrlPrefix: String,
  private val hintKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String? = null,
  private val tooltipKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String? = null,
) {
  GOOGLE(
    displayName = "Google",
    searchUrlPrefix = "https://www.google.com/search?q=",
    hintKey = "settings.browsers.search.engine.google.hint",
    tooltipKey = "settings.browsers.search.engine.google.tooltip",
  ),
  DUCKDUCKGO(
    displayName = "DuckDuckGo",
    searchUrlPrefix = "https://duckduckgo.com/?q=",
  ),
  BING(
    displayName = "Bing",
    searchUrlPrefix = "https://www.bing.com/search?q=",
  ),
  YAHOO(
    displayName = "Yahoo",
    searchUrlPrefix = "https://search.yahoo.com/search?p=",
  ),
  BRAVE(
    displayName = "Brave",
    searchUrlPrefix = "https://search.brave.com/search?q=",
  );

  val hint: @Nls String?
    get() = if (hintKey == null) null else IdeBundle.message(hintKey)

  val tooltip: @NlsContexts.Tooltip String?
    get() = if (tooltipKey == null) null else IdeBundle.message(tooltipKey)

  fun searchUrl(query: String): String =
    searchUrlPrefix + URLUtil.encodeURIComponent(query)
}
