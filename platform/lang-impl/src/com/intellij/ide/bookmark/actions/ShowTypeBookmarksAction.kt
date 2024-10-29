// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeNodeCache
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.NAVIGATABLE
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.containers.toArray
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Dimension
import javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

internal class ShowTypeBookmarksAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private val BookmarksManager.typeBookmarks
    get() = BookmarkType.values().mapNotNull { getBookmark(it)?.run { it to this } }.ifEmpty { null }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.bookmarksManager?.assignedTypes?.isNotEmpty() ?: false
  }

  override fun actionPerformed(event: AnActionEvent) {
    val bookmarks = event.bookmarksManager?.typeBookmarks ?: return
    val root = MyRoot(bookmarks.map { it.second })
    val tree = Tree(AsyncTreeModel(StructureTreeModel(MyStructure(root), root), root)).apply {
      isRootVisible = false
      showsRootHandles = false
      visibleRowCount = bookmarks.size
      selectionModel.selectionMode = SINGLE_TREE_SELECTION
    }
    bookmarks.forEach { tree.registerBookmarkTypeAction(root, it.first) }
    tree.registerEditSourceAction(root)
    tree.registerNavigateOnEnterAction()

    EditSourceOnDoubleClickHandler.install(tree)
    TreeUtil.promiseSelectFirstLeaf(tree).onSuccess {
      // show popup when tree is loaded
      val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(MyScrollPane(tree), tree)
        .setTitle(BookmarkBundle.message("popup.title.type.bookmarks"))
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnOtherWindowOpen(true)
        .createPopup()

      PopupUtil.setPopupToggleComponent(popup, event.inputEvent?.component)
      Disposer.register(popup, root)
      popup.showCenteredInCurrentWindow(event.project!!)
    }
  }


  private class MyScrollPane(val tree: Tree) : UiDataProvider, JBScrollPane(tree) {
    init {
      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
    }

    override fun getPreferredSize(): Dimension? = super.getPreferredSize()?.also {
      if (!isPreferredSizeSet) it.width = it.width.coerceAtMost(JBUI.scale(640))
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[NAVIGATABLE] = TreeUtil.getAbstractTreeNode(tree.selectionPath)
    }
  }


  private class MyRoot(bookmarks: List<Bookmark>) : Disposable, AbstractTreeNode<List<Bookmark>>(null, bookmarks) {
    private val cache = AbstractTreeNodeCache<Bookmark, AbstractTreeNode<*>>(this) { it.createNode() }

    override fun isAlwaysShowPlus() = true
    override fun getChildren() = cache.getNodes(value).onEach {
      if (it is BookmarkNode) it.bookmarkGroup = it.value.firstGroupWithDescription
    }

    override fun shouldUpdateData() = false
    override fun update(presentation: PresentationData) = Unit
    override fun dispose() = Unit
  }


  private class MyStructure(val root: MyRoot) : AbstractTreeStructure() {
    override fun commit() = Unit
    override fun hasSomethingToCommit() = false
    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?) = element as NodeDescriptor<*>
    override fun getRootElement() = root
    override fun getParentElement(element: Any): Any? = (element as? AbstractTreeNode<*>)?.parent
    override fun getChildElements(element: Any) = when (element) {
      root -> root.children.toArray(arrayOf())
      else -> emptyArray()
    }
  }
}
