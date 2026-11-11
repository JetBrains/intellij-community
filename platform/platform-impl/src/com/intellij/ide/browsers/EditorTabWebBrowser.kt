// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * A browser that shows pages in editor tabs, such as the IDE Browser. A plugin gives it with [EditorTabWebBrowserProvider].
 *
 * The browser has no path, no family and no specific settings. It opens a page only with [browse].
 */
@ApiStatus.Internal
abstract class EditorTabWebBrowser : WebBrowser() {
  final override fun getFamily(): BrowserFamily? = null

  final override fun getPath(): String? = null

  final override fun getSpecificSettings(): BrowserSpecificSettings? = null

  abstract override fun browse(url: String, project: Project?)

  /**
   * Creates the file of a new editor tab that shows [url] in [project].
   * Each call creates a new file, so each opened file is a new tab.
   * The caller opens the file where it wants the tab, see [createWebPageTabFile].
   */
  abstract fun createEditorTabFile(project: Project, url: String): VirtualFile
}
