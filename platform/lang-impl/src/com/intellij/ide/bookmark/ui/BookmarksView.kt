// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.bookmark.ui.tree.BookmarksTreeStructure
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.application.ModalityState.stateForComponent
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OPEN_IN_PREVIEW_TAB
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.preview.DescriptorPreview
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.RestoreSelectionListener
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.OpenSourceUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.containers.toArray
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil

class BookmarksView(val project: Project, showToolbar: Boolean?)
  : Disposable, DataProvider, OccurenceNavigator, OnePixelSplitter(false, .3f, .1f, .9f) {

  val isPopup = showToolbar == null

  private val state = BookmarksViewState.getInstance(project)
  private val preview = DescriptorPreview(this, false, null)

  private val selectionAlarm = SingleAlarm(this::selectionChanged, 50, stateForComponent(this), this)

  private val structure = BookmarksTreeStructure(this)
  private val model = StructureTreeModel(structure, this)
  val tree = Tree(AsyncTreeModel(model, this))
  private val treeExpander = DefaultTreeExpander(tree)
  private val panel = BorderLayoutPanel()

  val selectedNode
    get() = TreeUtil.getAbstractTreeNode(TreeUtil.getSelectedPathIfOne(tree))

  val selectedNodes
    get() = tree.selectionPaths?.map { TreeUtil.getAbstractTreeNode(it) }?.ifEmpty { null }

  private val leadSelectionNode
    get() = TreeUtil.getAbstractTreeNode(tree.leadSelectionPath)

  private val selectedSnapshot
    get() = (selectedNode as? BookmarkNode)?.run { bookmarkGroup?.let { GroupBookmarkSnapshot(it, value) } }


  override fun dispose() = preview.close()

  override fun getData(dataId: String): Any? {
    return when {
      PlatformDataKeys.TREE_EXPANDER.`is`(dataId) -> treeExpander
      CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> selectedNodes?.toArray(emptyArray<Navigatable?>())
      else -> null
    }
  }

  override fun getNextOccurenceActionName() = BookmarkBundle.message("bookmark.go.to.next.action.text")
  override fun getPreviousOccurenceActionName() = BookmarkBundle.message("bookmark.go.to.previous.action.text")

  override fun hasNextOccurence() = selectedSnapshot?.next != null
  override fun hasPreviousOccurence() = selectedSnapshot?.previous != null

  override fun goNextOccurence() = selectedSnapshot?.next?.let { go(it) }
  override fun goPreviousOccurence() = selectedSnapshot?.previous?.let { go(it) }
  private fun go(pair: Pair<BookmarkGroup, Bookmark>): OccurenceNavigator.OccurenceInfo? {
    TreeUtil.promiseSelect(tree, GroupBookmarkVisitor(pair.first, pair.second))
    return null
  }

  @Suppress("UNNECESSARY_SAFE_CALL")
  override fun saveProportion() = when (isPopup) {
    true -> state?.proportionPopup = proportion
    else -> state?.proportionView = proportion
  }

  override fun loadProportion() = when (isPopup) {
    true -> proportion = state.proportionPopup
    else -> proportion = state.proportionView
  }

  override fun setOrientation(vertical: Boolean) {
    super.setOrientation(vertical)
    selectionChanged(false)
  }

  val groupLineBookmarks = object : Option {
    override fun isEnabled() = !isPopup
    override fun isSelected() = isEnabled && state.groupLineBookmarks
    override fun setSelected(selected: Boolean) {
      state.groupLineBookmarks = selected
      model.invalidate()
    }
  }
  val autoScrollFromSource = object : Option {
    override fun isEnabled() = false // TODO: select in target
    override fun isSelected() = state.autoscrollFromSource
    override fun setSelected(selected: Boolean) {
      state.autoscrollFromSource = selected
      selectionAlarm.cancelAndRequest()
    }
  }
  val autoScrollToSource = object : Option {
    override fun isEnabled() = openInPreviewTab.run { !isSelected && isEnabled }
    override fun isSelected() = state.autoscrollToSource
    override fun setSelected(selected: Boolean) {
      state.autoscrollToSource = selected
      selectionAlarm.cancelAndRequest()
    }
  }
  val openInPreviewTab = object : Option {
    override fun isEnabled() = isVertical || !state.showPreview
    override fun isSelected() = UISettings.instance.openInPreviewTabIfPossible
    override fun setSelected(selected: Boolean) {
      UISettings.instance.openInPreviewTabIfPossible = selected
      selectionAlarm.cancelAndRequest()
    }
  }
  val showPreview = object : Option {
    override fun isAlwaysVisible() = !isVertical
    override fun isEnabled() = !isVertical && leadSelectionNode?.canNavigateToSource() ?: false
    override fun isSelected() = state.showPreview
    override fun setSelected(selected: Boolean) {
      state.showPreview = selected
      selectionAlarm.cancelAndRequest()
    }
  }

  private fun selectionChanged(autoScroll: Boolean = true) {
    if (isPopup || !openInPreviewTab.isEnabled) {
      preview.open(leadSelectionNode?.asDescriptor)
    }
    else {
      preview.close()
      if (autoScroll && (autoScrollToSource.isSelected || openInPreviewTab.isSelected)) {
        OpenSourceUtil.navigateToSource(false, false, leadSelectionNode)
      }
    }
  }

  init {
    panel.addToCenter(createScrollPane(tree, true))
    panel.putClientProperty(OPEN_IN_PREVIEW_TAB, true)

    firstComponent = panel

    tree.isRootVisible = false
    tree.showsRootHandles = !isPopup

    tree.emptyText.initialize(tree)
    tree.addTreeSelectionListener(RestoreSelectionListener())
    tree.addTreeSelectionListener { selectionAlarm.cancelAndRequest() }

    TreeUtil.promiseSelectFirstLeaf(tree)
    EditSourceOnEnterKeyHandler.install(tree)
    EditSourceOnDoubleClickHandler.install(tree)
    CustomizationUtil.installPopupHandler(tree, "Bookmarks.ToolWindow.PopupMenu", "popup@BookmarksView")

    project.messageBus.connect(this).subscribe(BookmarksListener.TOPIC, object : BookmarksListener {
      override fun groupsSorted() {
        model.invalidate() //TODO: node inserted
      }

      override fun groupAdded(group: BookmarkGroup) {
        model.invalidate() //TODO: node inserted
      }

      override fun groupRemoved(group: BookmarkGroup) {
        model.invalidate() //TODO: node removed
      }

      override fun groupRenamed(group: BookmarkGroup) {
        model.invalidate() //TODO: node updated
      }

      override fun bookmarksSorted(group: BookmarkGroup) {
        model.invalidate() //TODO: node inserted
      }

      override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark) {
        model.invalidate() //TODO: child node inserted
      }

      override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark) {
        model.invalidate() //TODO: child node removed
      }

      override fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark) {
        model.invalidate() //TODO: child node updated
      }

      override fun bookmarkTypeChanged(bookmark: Bookmark) {
        model.invalidate() //TODO: child node updated for every group
      }

      override fun defaultGroupChanged(oldGroup: BookmarkGroup?, newGroup: BookmarkGroup?) {
        model.invalidate() //TODO: node updated or node moved?
      }

      override fun structureChanged(node: Any) {
        model.invalidate(node, true)
      }
    })
  }
}
