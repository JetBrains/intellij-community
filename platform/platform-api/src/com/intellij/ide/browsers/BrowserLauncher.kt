// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

import java.io.File
import java.net.URI
import java.nio.file.Path

abstract class BrowserLauncher {
  companion object {
    @JvmStatic
    val instance: BrowserLauncher
      get() = ApplicationManager.getApplication().getService(BrowserLauncher::class.java)
  }

  abstract fun open(url: String)

  abstract fun browse(file: File)

  abstract fun browse(file: Path)

  fun browse(uri: URI): Unit = browse(uri.toString(), null, null)

  fun browse(url: String, browser: WebBrowser?): Unit = browse(url, browser, null)

  abstract fun browse(url: String, browser: WebBrowser? = null, project: Project? = null)

  @Deprecated("Implementation detail; please use other methods instead", level = DeprecationLevel.ERROR)
  fun browseUsingPath(url: String?,
                      browserPath: String? = null,
                      browser: WebBrowser? = null,
                      project: Project? = null,
                      openInNewWindow: Boolean = false,
                      additionalParameters: Array<String> = com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY): Boolean {
    browse(url!!, browser, project)
    return true
  }
}
