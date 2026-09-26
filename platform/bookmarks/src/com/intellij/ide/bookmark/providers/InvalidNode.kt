// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import java.io.File

internal class UrlNode(project: Project, bookmark: InvalidBookmark) : BookmarkNode<InvalidBookmark>(project, bookmark) {

  private val cachedVirtualFile: VirtualFile? by lazy {
    VirtualFileManager.getInstance().findFileByUrl(value.url)
  }

  override fun getVirtualFile(): VirtualFile? = cachedVirtualFile

  override fun getChildren(): List<AbstractTreeNode<*>> = emptyList()

  override fun update(presentation: PresentationData) {
    presentation.setIcon(IconUtil.desaturate(wrapIcon(null)))
    val line = value.line + 1
    val url = value.url
    val index = when (val slash = '/') {
      File.separatorChar -> url.lastIndexOf(slash)
      else -> url.lastIndexOfAny(charArrayOf(slash, File.separatorChar))
    }
    val name = if (index < 0) url else url.substring(index + 1)
    val location: @NlsSafe String? = if (index <= 0) null else url.substring(0, index)
    val description = bookmarkDescription
    if (description == null) {
      presentation.presentableText = name // configure speed search
      presentation.addText(name, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      if (line > 0) presentation.addText(" :$line", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      location?.let { presentation.addText("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    }
    else {
      presentation.presentableText = "$description $name" // configure speed search
      presentation.addText(description, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      presentation.addText("  $name", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      if (line > 0) presentation.addText(" :$line", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      location?.let { presentation.addText("  ($it)", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    }
  }
}
