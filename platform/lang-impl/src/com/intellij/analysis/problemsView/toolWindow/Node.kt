// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.tree.LeafState
import com.intellij.ui.tree.TreePathUtil.pathToCustomNode
import javax.swing.tree.TreePath

abstract class Node : PresentableNodeDescriptor<Node?>, LeafState.Supplier {
  protected constructor(project: Project) : super(project, null)
  protected constructor(parent: Node) : super(parent.project, parent)

  open val descriptor: OpenFileDescriptor?
    get() = null

  protected abstract fun update(project: Project, presentation: PresentationData)

  abstract override fun getName(): String

  override fun toString(): String = name

  open fun getChildren(): Collection<Node> = emptyList()

  open fun getVirtualFile(): VirtualFile? = null

  open fun getNavigatable(): Navigatable? = descriptor

  override fun getElement(): Node = this

  override fun update(presentation: PresentationData) {
    if (myProject == null || myProject.isDisposed) return
    update(myProject, presentation)
  }

  fun getPath(): TreePath = pathToCustomNode(this) { node: Node? -> node?.getParent(Node::class.java) }!!

  fun <T> getParent(type: Class<T>): T? {
    val parent = parentDescriptor ?: return null
    @Suppress("UNCHECKED_CAST")
    if (type.isInstance(parent)) return parent as T
    throw IllegalStateException("unexpected node " + parent.javaClass)
  }

  fun <T> findAncestor(type: Class<T>): T? {
    var parent = parentDescriptor
    while (parent != null) {
      @Suppress("UNCHECKED_CAST")
      if (type.isInstance(parent)) return parent as T
      parent = parent.parentDescriptor
    }
    return null
  }
}
