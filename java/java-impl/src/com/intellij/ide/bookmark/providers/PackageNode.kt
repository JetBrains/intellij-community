// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.impl.CompoundIconProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

internal class PackageNode(project: Project, bookmark: PackageBookmark) : BookmarkNode<PackageBookmark>(project, bookmark) {

  override fun getChildren() = emptyList<AbstractTreeNode<*>>()

  override fun update(presentation: PresentationData) {
    presentation.setIcon(wrapIcon(CompoundIconProvider.findIcon(value?.element?.`package`, 0)))
    presentation.tooltip = bookmarkDescription
    presentation.presentableText = value?.name
    presentation.locationString = value?.module
  }
}
