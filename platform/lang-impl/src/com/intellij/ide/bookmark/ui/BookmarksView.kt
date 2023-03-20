// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.execution.Location
import com.intellij.ide.DefaultTreeExpander
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.bookmark.*
import com.intellij.ide.bookmark.actions.registerNavigateOnEnterAction
import com.intellij.ide.bookmark.ui.tree.BookmarksTreeStructure
import com.intellij.ide.bookmark.ui.tree.FolderNodeComparator
import com.intellij.ide.bookmark.ui.tree.FolderNodeUpdater
import com.intellij.ide.bookmark.ui.tree.VirtualFileVisitor
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.stateForComponent
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.Companion.OPEN_IN_PREVIEW_TAB
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.preview.DescriptorPreview
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.RestoreSelectionListener
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.OpenSourceUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.toArray
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

class BookmarksView(val project: Project, showToolbar: Boolean?)
  : Disposable, DataProvider, OccurenceNavigator, OnePixelSplitter(false, .3f, .1f, .9f) {

  companion object {
    val BOOKMARKS_VIEW: DataKey<BookmarksView> = DataKey.create("BOOKMARKS_VIEW")
  }

  val isPopup = showToolbar == null

  fun interface EditSourceListener { fun onEditSource() }
  private val editSourceListeners: MutableList<EditSourceListener> = mutableListOf()

  private val state = BookmarksViewState.getInstance(project)
  private val preview = DescriptorPreview(this, false, null)

  private val selectionAlarm = SingleAlarm(this::selectionChanged, 50, this, ThreadToUse.SWING_THREAD, stateForComponent(this))

  private val structure = BookmarksTreeStructure(this)
  val model = StructureTreeModel(structure, FolderNodeComparator(project), this)
  val tree = DnDAwareTree(AsyncTreeModel(model, this))
  private val treeExpander = DefaultTreeExpander(tree)
  private val panel = BorderLayoutPanel()
  private val updater = FolderNodeUpdater(this)
  private val ideView = IdeViewForBookmarksView(this)

  val selectedNode
    get() = TreeUtil.getAbstractTreeNode(TreeUtil.getSelectedPathIfOne(tree))

  val selectedNodes
    get() = tree.selectionPaths?.mapNotNull { TreeUtil.getAbstractTreeNode(it) }?.ifEmpty { null }

  private val previousOccurrence
    get() = when (val occurrence = selectedNode?.bookmarkOccurrence) {
      null -> BookmarkOccurrence.lastLineBookmark(project)
      else -> occurrence.previousLineBookmark()
    }

  private val nextOccurrence
    get() = when (val occurrence = selectedNode?.bookmarkOccurrence) {
      null -> BookmarkOccurrence.firstLineBookmark(project)
      else -> occurrence.nextLineBookmark()
    }


  override fun dispose() = preview.close()

  override fun getData(dataId: String): Any? = when {
    BOOKMARKS_VIEW.`is`(dataId) -> this
    LangDataKeys.IDE_VIEW.`is`(dataId) -> ideView
    PlatformDataKeys.TREE_EXPANDER.`is`(dataId) -> treeExpander
    PlatformDataKeys.SELECTED_ITEMS.`is`(dataId) -> selectedNodes?.toArray(emptyArray<Any>())
    PlatformDataKeys.SELECTED_ITEM.`is`(dataId) -> selectedNodes?.firstOrNull()
    PlatformDataKeys.BGT_DATA_PROVIDER.`is`(dataId) -> {
      val selectedNodes = selectedNodes
      DataProvider { slowDataId -> getSlowData(slowDataId, selectedNodes) }
    }
    else -> null
  }

  @RequiresBackgroundThread
  private fun getSlowData(dataId: String, selection: List<AbstractTreeNode<*>>?): Any? = when {
    PlatformDataKeys.VIRTUAL_FILE.`is`(dataId) -> selection?.firstOrNull()?.asVirtualFile
    PlatformDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> selection?.mapNotNull { it.asVirtualFile }?.ifEmpty { null }?.toTypedArray()
    PlatformCoreDataKeys.MODULE.`is`(dataId) -> selection?.firstOrNull()?.module
    Location.DATA_KEY.`is`(dataId) -> selection?.firstOrNull()?.location
    else -> null
  }

  override fun getNextOccurenceActionName() = BookmarkBundle.message("bookmark.go.to.next.occurence.action.text")
  override fun getPreviousOccurenceActionName() = BookmarkBundle.message("bookmark.go.to.previous.occurence.action.text")

  override fun hasNextOccurence() = nextOccurrence != null
  override fun hasPreviousOccurence() = previousOccurrence != null

  override fun goNextOccurence() = nextOccurrence?.let { go(it) }
  override fun goPreviousOccurence() = previousOccurrence?.let { go(it) }
  private fun go(occurrence: BookmarkOccurrence): OccurenceNavigator.OccurenceInfo? {
    select(occurrence.group, occurrence.bookmark).onSuccess { navigateToSource(true) }
    return null
  }

  fun select(file: VirtualFile) = updater.updateImmediately { select(VirtualFileVisitor(file, null), true) }
  fun select(group: BookmarkGroup) = select(GroupBookmarkVisitor(group), true)
  fun select(group: BookmarkGroup, bookmark: Bookmark) = select(GroupBookmarkVisitor(group, bookmark), false)
  private fun select(visitor: TreeVisitor, centered: Boolean) = TreeUtil.promiseMakeVisible(tree, visitor).onSuccess {
    tree.selectionPath = it
    TreeUtil.scrollToVisible(tree, it, centered)
    if (!tree.hasFocus()) selectionChanged()
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
      model.invalidateAsync()
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
    override fun isEnabled() = openInPreviewTab.isEnabled
    override fun isSelected() = state.autoscrollToSource
    override fun setSelected(selected: Boolean) {
      state.autoscrollToSource = selected
      selectionAlarm.cancelAndRequest()
    }
  }
  val openInPreviewTab = object : Option {
    override fun isEnabled() = isVertical || !state.showPreview
    override fun isSelected() = UISettings.getInstance().openInPreviewTabIfPossible
    override fun setSelected(selected: Boolean) {
      UISettings.getInstance().openInPreviewTabIfPossible = selected
      selectionAlarm.cancelAndRequest()
    }
  }
  val showPreview = object : Option {
    override fun isAlwaysVisible() = !isVertical
    override fun isEnabled() = !isVertical && selectedNode?.canNavigateToSource() ?: false
    override fun isSelected() = state.showPreview
    override fun setSelected(selected: Boolean) {
      state.showPreview = selected
      selectionAlarm.cancelAndRequest()
    }
  }

  private fun selectionChanged(autoScroll: Boolean = tree.hasFocus()) {
    if (isPopup || !openInPreviewTab.isEnabled) {
      preview.open(selectedNode?.asDescriptor)
    }
    else {
      preview.close()
      if (autoScroll && (autoScrollToSource.isSelected || openInPreviewTab.isSelected)) {
        navigateToSource(false)
      }
    }
  }

  private fun navigateToSource(requestFocus: Boolean) {
    val node = selectedNode ?: return
    if (node.asVirtualFile?.fileType == FileTypes.UNKNOWN) { return }
    val task = Runnable { OpenSourceUtil.navigateToSource(requestFocus, false, node) }
    ApplicationManager.getApplication()?.invokeLater(task, stateForComponent(tree)) { project.isDisposed }
  }

  fun addEditSourceListener(listener: EditSourceListener) {
    editSourceListeners.add(listener)
  }

  init {
    panel.addToCenter(createScrollPane(tree, true))
    panel.putClientProperty(OPEN_IN_PREVIEW_TAB, true)

    firstComponent = panel

    tree.isHorizontalAutoScrollingEnabled = false
    tree.isRootVisible = false
    tree.showsRootHandles = true // TODO: fix auto-expand
    if (!isPopup) {
      val handler = DragAndDropHandler(this)
      DnDSupport.createBuilder(tree)
        .setDisposableParent(this)
        .setBeanProvider(handler::createBean)
        .setDropHandlerWithResult(handler)
        .setTargetChecker(handler)
        .enableAsNativeTarget()
        .install()
    }

    tree.emptyText.initialize(tree)
    tree.addTreeSelectionListener(RestoreSelectionListener())
    tree.addTreeSelectionListener { if (tree.hasFocus()) selectionAlarm.cancelAndRequest() }
    tree.addFocusListener(object : FocusListener {
      override fun focusLost(event: FocusEvent?) = Unit
      override fun focusGained(event: FocusEvent?) = selectionAlarm.cancelAndRequest()
    })

    TreeUIHelper.getInstance().installTreeSpeedSearch(tree)
    TreeUtil.promiseSelectFirstLeaf(tree)
    tree.registerNavigateOnEnterAction { editSourceListeners.forEach { it.onEditSource() } }
    EditSourceOnDoubleClickHandler.install(tree) { editSourceListeners.forEach { it.onEditSource() } }

    val group = ContextMenuActionGroup(tree)
    val handler = PopupHandler.installPopupMenu(tree, group, ActionPlaces.BOOKMARKS_VIEW_POPUP)
    PopupMenuPreloader.install(tree, ActionPlaces.BOOKMARKS_VIEW_POPUP, handler) { group }

    project.messageBus.connect(this).subscribe(BookmarksListener.TOPIC, object : BookmarksListener {
      override fun groupsSorted() {
        model.invalidateAsync() //TODO: node inserted
      }

      override fun groupAdded(group: BookmarkGroup) {
        model.invalidateAsync() //TODO: node inserted
      }

      override fun groupRemoved(group: BookmarkGroup) {
        model.invalidateAsync() //TODO: node removed
      }

      override fun groupRenamed(group: BookmarkGroup) {
        model.invalidateAsync() //TODO: node updated
      }

      override fun bookmarksSorted(group: BookmarkGroup) {
        model.invalidateAsync() //TODO: node inserted
      }

      override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark) {
        model.invalidateAsync() //TODO: child node inserted
      }

      override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark) {
        model.invalidateAsync() //TODO: child node removed
      }

      override fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark) {
        model.invalidateAsync() //TODO: child node updated
      }

      override fun bookmarkTypeChanged(bookmark: Bookmark) {
        model.invalidateAsync() //TODO: child node updated for every group
      }

      override fun defaultGroupChanged(oldGroup: BookmarkGroup?, newGroup: BookmarkGroup?) {
        model.invalidateAsync() //TODO: node updated or node moved?
      }

      override fun structureChanged(node: Any?) {
        when (node) {
          null -> model.invalidateAsync()
          else -> model.invalidateAsync(node, true)
        }
      }
    })
  }
}
