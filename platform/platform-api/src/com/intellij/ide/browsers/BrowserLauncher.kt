/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.browsers

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.ArrayUtil

import java.io.File
import java.net.URI

abstract class BrowserLauncher {
  companion object {
    @JvmStatic
    val instance: BrowserLauncher
      get() = ServiceManager.getService(BrowserLauncher::class.java)
  }

  abstract fun open(url: String)

  fun browse(uri: URI) = browse(uri, null)

  fun browse(uri: URI, project: Project?) {
    browse(uri.toString(), project = project)
  }

  abstract fun browse(file: File)

  fun browse(url: String, browser: WebBrowser?) {
    browse(url, browser, null)
  }

  abstract fun browse(url: String, browser: WebBrowser? = null, project: Project? = null)

  fun browseUsingPath(url: String?,
                      browserPath: String? = null,
                      browser: WebBrowser? = null,
                      project: Project? = null,
                      additionalParameters: Array<String> = ArrayUtil.EMPTY_STRING_ARRAY): Boolean {
    return browseUsingPath(url, browserPath, browser, project, false, additionalParameters)
  }

  abstract fun browseUsingPath(url: String?,
                               browserPath: String? = null,
                               browser: WebBrowser? = null,
                               project: Project? = null,
                               openInNewWindow: Boolean = false,
                               additionalParameters: Array<String> = ArrayUtil.EMPTY_STRING_ARRAY): Boolean
}