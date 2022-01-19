// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import java.io.File

private val STRIKEOUT = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null)

internal class UrlNode(project: Project, bookmark: InvalidBookmark) : BookmarkNode<InvalidBookmark>(project, bookmark) {

  override fun getChildren() = emptyList<AbstractTreeNode<*>>()

  override fun update(presentation: PresentationData) {
    presentation.setIcon(wrapIcon(null))
    val line = value.line + 1
    val url = value.url
    val index = when (val slash = '/') {
      File.separatorChar -> url.lastIndexOf(slash)
      else -> url.lastIndexOfAny(charArrayOf(slash, File.separatorChar))
    }
    val name = if (index < 0) url else url.substring(index + 1)
    val location = if (index <= 0) null else url.substring(0, index)
    val description = bookmarkDescription
    if (description == null) {
      presentation.presentableText = name // configure speed search
      presentation.addText(name, STRIKEOUT)
      if (line > 0) presentation.addText(" :$line", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      location?.let { presentation.addText("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    }
    else {
      presentation.presentableText = "$description $name" // configure speed search
      presentation.addText(description, STRIKEOUT)
      presentation.addText("  $name", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      if (line > 0) presentation.addText(" :$line", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      location?.let { presentation.addText("  ($it)", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    }
  }
}
