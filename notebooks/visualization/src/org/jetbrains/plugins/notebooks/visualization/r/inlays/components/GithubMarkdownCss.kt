/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.util.*

/** github-markdown.css as String, dependent form current IDE theme. */
class GithubMarkdownCss {
  companion object {

    private var storedCss: String? = null

    private var isStoredDarkula = false

    private fun getResourceAsString(resource: String): String {
      val inputStream = GithubMarkdownCss::class.java.classLoader.getResourceAsStream(resource)
      val scanner = Scanner(inputStream).useDelimiter("\\A")
      return if (scanner.hasNext()) scanner.next() else ""
    }

    val css: String
      get() {
        if (storedCss != null && isStoredDarkula == UIUtil.isUnderDarcula()) {
          return storedCss!!
        }

        storedCss = if (UIUtil.isUnderDarcula()) {
          isStoredDarkula = true
          getResourceAsString("/css/github-darcula.css")
        }
        else {
          getResourceAsString("/css/github-intellij.css")
        }

        val index = storedCss!!.indexOf("font-size: 16px;")
        if (index != -1) {
          storedCss = storedCss!!.replaceRange(index + 11, index + 13, JBUI.scaleFontSize(14f).toString())
        }

        return storedCss!!
      }
  }
}