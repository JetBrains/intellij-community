// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.impl.CompoundIconProvider.findIcon
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiUtilCore.findFileSystemItem
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState
import java.util.Objects.hash

internal class FileNode(parent: Node, val file: VirtualFile) : Node(parent) {

  override fun getLeafState() = if (parentDescriptor is Root) LeafState.NEVER else LeafState.DEFAULT

  override fun getName() = file.presentableName ?: file.name

  override fun getVirtualFile() = file

  override fun getDescriptor() = project?.let { OpenFileDescriptor(it, file) }

  override fun update(project: Project, presentation: PresentationData) {
    presentation.addText(name, REGULAR_ATTRIBUTES)
    presentation.setIcon(findIcon(findFileSystemItem(project, file), 0) ?: when (file.isDirectory) {
      true -> AllIcons.Nodes.Folder
      else -> AllIcons.FileTypes.Any_type
    })
    if (parentDescriptor !is FileNode) {
      val url = file.parent?.presentableUrl ?: return
      presentation.addText("  ${getLocationRelativeToUserHome(url)}", GRAYED_ATTRIBUTES)
    }
    val root = findAncestor(Root::class.java)
    val count = root?.getFileProblemCount(file) ?: 0
    if (count > 0) {
      val text = ProblemsViewBundle.message("problems.view.file.problems", count)
      presentation.addText("  $text", GRAYED_ATTRIBUTES)
    }
  }

  override fun getChildren(): Collection<Node> {
    val root = findAncestor(Root::class.java)
    return root?.getChildren(file) ?: super.getChildren()
  }

  override fun hashCode() = hash(project, file)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? FileNode ?: return false
    return that.project == project && that.file == file
  }
}
