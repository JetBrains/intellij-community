// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WebPageTabs")

package com.intellij.ide.browsers

import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.actions.WebPreviewFileType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.fileEditor.impl.HTMLVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Creates the file of a new editor tab that shows the page at [url] in [project].
 *
 * The tab is a browser tab when an [EditorTabWebBrowser] is available, for example an IDE Browser tab.
 * Otherwise, it is an IDE HTML editor tab with [title].
 * Each call creates a new file, so each opened file is a new tab.
 * The caller opens the file where it wants the tab. To open it in the current editor window, use [browseInEditorTab].
 */
@ApiStatus.Internal
fun createWebPageTabFile(project: Project, url: String, title: @DialogTitle String): VirtualFile =
  EditorTabWebBrowserProvider.getAvailableBrowsers().firstOrNull()?.createEditorTabFile(project, url)
  ?: HTMLVirtualFile.createFile(project, title, HTMLEditorProvider.Request.url(url), WebPreviewFileType.INSTANCE, ignoreJcef = false)

/**
 * Opens the page at [url] in a new editor tab of [project], see [createWebPageTabFile].
 *
 * When no editor can show the tab, for example without JCEF, the page opens in the default browser.
 */
@ApiStatus.Internal
suspend fun browseInEditorTab(project: Project, url: String, title: @DialogTitle String, focusEditor: Boolean = true) {
  val file = createWebPageTabFile(project, url, title)
  if (FileEditorProviderManager.getInstance().getProvidersAsync(project, file).isEmpty()) {
    BrowserUtil.browse(url, project)
    return
  }
  FileEditorManager.getInstance(project).openFile(file, FileEditorOpenRequest.withFocus(focusEditor))
}
