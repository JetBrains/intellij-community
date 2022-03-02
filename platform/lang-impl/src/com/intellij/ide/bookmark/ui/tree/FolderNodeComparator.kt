// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.ProjectViewPane.ID
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.project.Project

class FolderNodeComparator(project: Project) : GroupByTypeComparator(project, ID) {

  override fun compare(descriptor1: NodeDescriptor<*>?, descriptor2: NodeDescriptor<*>?) = when {
    (descriptor1 as? ProjectViewNode<*>)?.parent?.parentFolderNode == null -> 0
    (descriptor2 as? ProjectViewNode<*>)?.parent?.parentFolderNode == null -> 0
    else -> super.compare(descriptor1, descriptor2)
  }
}
