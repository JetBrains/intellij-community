// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.Path

abstract class BrowserLauncher {
  companion object {
    @JvmStatic
    val instance: BrowserLauncher
      get() = ApplicationManager.getApplication().getService(BrowserLauncher::class.java)
  }

  abstract fun open(url: String)

  abstract fun browse(file: Path)

  fun browse(uri: URI): Unit = browse(uri.toString(), browser = null)

  fun browse(url: String, browser: WebBrowser?): Unit = browse(url, browser, project = null)

  abstract fun browse(url: String, browser: WebBrowser? = null, project: Project? = null)

  /**
   * Opens [url] in a browser outside the IDE.
   * It is the default browser, unless the default browser shows pages in editor tabs, such as the IDE Browser.
   * Then it is the system default browser.
   * This implementation opens [url] as [browse] does without a browser, so it can open an editor tab.
   */
  @ApiStatus.Internal
  open fun browseInExternalBrowser(url: String, project: Project?): Unit = browse(url, browser = null, project)
}
