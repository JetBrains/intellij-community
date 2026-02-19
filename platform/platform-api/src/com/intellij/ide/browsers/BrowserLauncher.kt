// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  /** Prefer `browse(Path)`. */
  @ApiStatus.Obsolete
  abstract fun browse(file: java.io.File)

  abstract fun browse(file: Path)

  fun browse(uri: URI): Unit = browse(uri.toString(), null, null)

  fun browse(url: String, browser: WebBrowser?): Unit = browse(url, browser, null)

  abstract fun browse(url: String, browser: WebBrowser? = null, project: Project? = null)
}
