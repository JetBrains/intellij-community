// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.ArrayUtil

import java.io.File
import java.net.URI
import java.nio.file.Path

abstract class BrowserLauncher {
  companion object {
    @JvmStatic
    val instance: BrowserLauncher
      get() = ServiceManager.getService(BrowserLauncher::class.java)
  }

  abstract fun open(url: String)

  abstract fun browse(file: File)

  abstract fun browse(file: Path)

  fun browse(uri: URI): Unit = browse(uri.toString(), null, null)

  fun browse(url: String, browser: WebBrowser?): Unit = browse(url, browser, null)

  abstract fun browse(url: String, browser: WebBrowser? = null, project: Project? = null)

  abstract fun browseUsingPath(url: String?,
                               browserPath: String? = null,
                               browser: WebBrowser? = null,
                               project: Project? = null,
                               openInNewWindow: Boolean = false,
                               additionalParameters: Array<String> = ArrayUtil.EMPTY_STRING_ARRAY): Boolean
}