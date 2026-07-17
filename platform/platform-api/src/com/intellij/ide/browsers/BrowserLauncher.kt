// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.net.URI
import java.nio.file.Path

abstract class BrowserLauncher {
  companion object {
    @JvmStatic
    val instance: BrowserLauncher
      get() = ApplicationManager.getApplication().getService(BrowserLauncher::class.java)
  }

  abstract fun open(url: String)

  @Deprecated(message = "Use browse(Path) instead", level = DeprecationLevel.ERROR)
  @Suppress("IO_FILE_USAGE", "DeprecatedCallableAddReplaceWith")
  open fun browse(file: java.io.File): Unit = browse(file.toPath())

  abstract fun browse(file: Path)

  fun browse(uri: URI): Unit = browse(uri.toString(), browser = null)

  fun browse(url: String, browser: WebBrowser?): Unit = browse(url, browser, project = null)

  abstract fun browse(url: String, browser: WebBrowser? = null, project: Project? = null)
}
