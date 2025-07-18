// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.*
import com.intellij.ide.IdeBundle
import com.intellij.ide.OccurenceNavigatorSupport
import com.intellij.ide.actions.OccurenceNavigatorActionBase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.rpc.NavigatableId
import com.intellij.ide.rpc.navigatable
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.buildView.BuildTreeApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.ui.*
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.split.SplitComponentId
import com.intellij.ui.tree.TreePathUtil
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.awt.*
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.*

private val LOG = fileLogger()

internal class BuildTreeView(parentScope: CoroutineScope, private val buildViewId: SplitComponentId)
  : JPanel(), UiDataProvider, ComponentContainer {
  private val uiScope = parentScope.childScope("BuildTreeView", Dispatchers.UI + ModalityState.any().asContextElement())

  private val rootNode = MyNode(
    BuildTreeNode(BuildTreeNode.ROOT_ID, BuildTreeNode.NO_ID,
                  null, null, null, null, null, emptyList(), false, false, false, false, false))
  private val buildProgressRootNode = MyNode(
    BuildTreeNode(BuildTreeNode.BUILD_PROGRESS_ROOT_ID, BuildTreeNode.ROOT_ID,
                  AnimatedIcon.Default.INSTANCE.rpcId(), null, "", null, null, emptyList(), true, false, true, false, false))
  private val treeModel = DefaultTreeModel(rootNode)
  init {
    rootNode.addChild(buildProgressRootNode)
  }

  private val tree = createUI()

  private var filteringState = BuildTreeFilteringState(false, true)

  private val navigationContext = MutableStateFlow(BuildTreeNavigationContext(false, false, false))

  private val occurenceNavigatorSupport = MyOccurenceNavigatorSupport(tree)

  init {
    LOG.debug { "Creating BuildTreeView(id=$buildViewId)" }
    uiScope.launch {
      val nodeMap = mutableMapOf(buildProgressRootNode.id to buildProgressRootNode)
      BuildTreeApi.getInstance().getTreeEventsFlow(buildViewId).collect { event ->
        handleTreeEvent(event, nodeMap)
      }
    }
    uiScope.launch {
      BuildTreeApi.getInstance().getFilteringStateFlow(buildViewId).collect {
        handleFilteringStateChange(it)
      }
    }
    uiScope.launch(Dispatchers.EDT /* Navigatable-s might expect WIL to be taken */) {
      BuildTreeApi.getInstance().getNavigationFlow(buildViewId).collect {
        handleNavigation(it.forward)
      }
    }
    uiScope.launch {
      navigationContext.collect {
        LOG.debug { "Navigation context: $it" }
        BuildTreeApi.getInstance().onNavigationContextChange(buildViewId, it)
      }
    }
    uiScope.launch {
      BuildTreeApi.getInstance().getShutdownStateFlow(buildViewId).collect {
        if (it) {
          LOG.debug { "Disposing BuildTreeView(id=$buildViewId)" }
          Disposer.dispose(this@BuildTreeView)
        }
      }
    }
  }

  private fun createUI(): Tree {
    val tree = Tree(treeModel)
    tree.setLargeModel(true)
    tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    tree.putClientProperty(DefaultTreeUI.AUTO_EXPAND_ALLOWED, false)
    tree.setRootVisible(false)
    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)
    TreeSpeedSearch.installOn(tree).comparator = SpeedSearchComparator(false)
    TreeUtil.installActions(tree)
    tree.setCellRenderer(MyNodeRenderer())
    tree.putClientProperty(RenderingHelper.SHRINK_LONG_RENDERER, true)
    tree.getAccessibleContext().setAccessibleName(IdeBundle.message("buildToolWindow.tree.accessibleName"))
    tree.addTreeSelectionListener(::onTreeSelectionChanged)
    tree.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component?, x: Int, y: Int) {
        val actionManager = ActionManager.getInstance()
        val groupId = "BuildTree"
        val group = actionManager.getAction(groupId) as? ActionGroup
        if (group == null) {
          LOG.warn("'$groupId' context menu action group not found")
          return
        }
        val popupMenu = actionManager.createActionPopupMenu("BuildView", group)
        popupMenu.setTargetComponent(tree)
        val menu = popupMenu.getComponent()
        menu.show(comp, x, y)
      }
    })

    setLayout(CardLayout())
    add(ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE), "tree")
    if (isNewUI()) {
      UIUtil.setBackgroundRecursively(this, JBUI.CurrentTheme.ToolWindow.background())
    }

    return tree
  }

  override fun getComponent(): JComponent {
    return this
  }

  override fun getPreferredFocusableComponent(): JComponent {
    return tree
  }

  override fun dispose() {
    uiScope.cancel()
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CommonDataKeys.NAVIGATABLE_ARRAY] = extractSelectedNodesNavigatables()
    sink[CommonDataKeys.NAVIGATABLE] = extractSelectedNodeNavigatable()
    sink[BUILD_TREE_SELECTED_NODE] = getSelectedNode()?.let { SelectedBuildTreeNode(it.id) }
  }

  private fun handleTreeEvent(event: BuildTreeEvent, nodeMap: MutableMap<Int, MyNode>) {
    when (event) {
      is BuildNodesUpdate -> {
        val nodeInfos = event.nodes
        if (nodeInfos.isEmpty()) {
          LOG.debug("Clearing nodes")
          nodeMap.clear()
          rootNode.removeChildren()
        }
        else {
          nodeInfos.forEach { nodeInfo ->
            val node = nodeMap[nodeInfo.id]
            if (node == null) {
              val parentNode = if (nodeInfo.parentId == BuildTreeNode.ROOT_ID) rootNode else nodeMap[nodeInfo.parentId]
              if (parentNode == null) {
                LOG.warn("Cannot create node (id=${nodeInfo.id}), parent not found (id=${nodeInfo.parentId})")
                return@forEach
              }
              assert(nodeInfo.id > 0)
              LOG.debug { "Creating new node (id=${nodeInfo.id}, parentId=${nodeInfo.parentId})" }
              val newNode = MyNode(nodeInfo)
              nodeMap[nodeInfo.id] = newNode
              if (parentNode.addChild(newNode)) {
                maybeExpand(TreePathUtil.toTreePath(parentNode))
              }
            }
            else {
              LOG.debug { "Updating node (id=${nodeInfo.id})" }
              node.content = nodeInfo
            }
          }
        }
        updateNavigationContext()
      }
      is BuildTreeExposeRequest -> {
        val nodeId = event.nodeId
        if (nodeId == null) {
          LOG.debug { "Clearing selection" }
          tree.clearSelection()
        }
        else {
          val node = nodeMap[nodeId]
          if (node == null) {
            LOG.warn("Node not found for id=$nodeId")
          }
          else {
            if (event.alsoSelect) {
              LOG.debug { "Select (id=$nodeId)" }
              TreeUtil.selectNode(tree, node)
            }
            else {
              LOG.debug { "Make visible (id=$nodeId)" }
              tree.makeVisible(TreePathUtil.toTreePath(node))
            }
          }
        }
      }
    }
  }

  private fun handleFilteringStateChange(filteringState: BuildTreeFilteringState) {
    LOG.debug { "Filtering state update: $filteringState" }
    this.filteringState = filteringState
    rootNode.reload()
    updateNavigationContext()
  }

  private fun handleNavigation(forward: Boolean) {
    val info = if (forward) occurenceNavigatorSupport.goNextOccurence() else occurenceNavigatorSupport.goPreviousOccurence()
    if (info == null) {
      LOG.debug { "No occurrence found (forward=$forward)" }
    }
    else {
      val navigatable = info.navigateable
      if (navigatable != null && navigatable.canNavigate()) {
        LOG.debug { "Performing navigation (forward=$forward)" }
        navigatable.navigate(true)
      }
      else {
        LOG.debug { "Navigation target not available: $navigatable" }
      }

      val project = ProjectUtil.getProjectForComponent(this)
      if (project == null) {
        LOG.warn("Project not found for BuildTreeView(id=$buildViewId)")
      }
      else {
        OccurenceNavigatorActionBase.displayOccurrencesInfoInStatusBar(project, info.occurenceNumber, info.occurencesCount)
      }
    }
  }

  private fun onTreeSelectionChanged(e: TreeSelectionEvent) {
    if (e.path != null) {
      val selectedNode = tree.selectionPath?.lastPathComponent as? MyNode
      val selectedNodeId = selectedNode?.id
      uiScope.launch {
        LOG.debug { "Selection change: $selectedNodeId" }
        BuildTreeApi.getInstance().onSelectionChange(buildViewId, selectedNodeId)
      }
      updateNavigationContext()
    }
  }

  private fun updateNavigationContext() {
    val hasPrevNode = occurenceNavigatorSupport.hasPreviousOccurence()
    val hasNextNode = occurenceNavigatorSupport.hasNextOccurence()
    val hasAnyNode = hasNextNode || hasPrevNode || (tree.selectionPath?.lastPathComponent as? MyNode)?.occurrenceNavigatable != null
    navigationContext.value = BuildTreeNavigationContext(hasPrevNode, hasNextNode, hasAnyNode)
  }

  fun maybeExpand(path: TreePath?): Boolean {
    if (path == null) {
      return false
    }
    val last = path.lastPathComponent
    if (last is MyNode) {
      var expanded = false
      val children = last.getVisibleChildren()
      if (children.isNotEmpty()) {
        children.forEach {
          expanded = maybeExpand(path.pathByAddingChild(it)) || expanded
        }
        if (expanded) {
          return true
        }
        if (last.content.autoExpand && !tree.isExpanded(path)) {
          tree.expandPath(path)
          return true
        }
      }
    }
    return false
  }

  private fun getSelectedNode(): MyNode? {
    return tree.selectionPaths?.singleOrNull()?.lastPathComponent as? MyNode
  }

  private fun extractSelectedNodeNavigatable(): Navigatable? {
    val selectedNode = getSelectedNode() ?: return null
    return selectedNode.content.navigatables.singleOrNull()?.navigatable()
  }

  private fun extractSelectedNodesNavigatables(): Array<Navigatable>? {
    val selectedNodes = TreeUtil.collectSelectedObjects(tree) { (it?.lastPathComponent as? MyNode)?.content }.filterNotNull()
    val navigatables = selectedNodes.flatMap { it.navigatables }.map { it.navigatable() }
    return if (navigatables.isEmpty()) null else navigatables.toTypedArray()
  }

  private class MyOccurenceNavigatorSupport(tree: JTree) : OccurenceNavigatorSupport(tree) {
    override fun createDescriptorForNode(node: DefaultMutableTreeNode): Navigatable? {
        return (node as? MyNode)?.occurrenceNavigatable?.navigatable()
    }

    override fun getNextOccurenceActionName() = ""
    override fun getPreviousOccurenceActionName() = ""
  }

  private inner class MyNode(content: BuildTreeNode) : DefaultMutableTreeNode(content) {
    private var cachedVisibleChildren: MutableList<MyNode>? = null
    private var cachedFilteringState: BuildTreeFilteringState? = null

    var content: BuildTreeNode
      get() = userObject as BuildTreeNode
      set(newContent) {
        updateContent(newContent)
      }

    val id: Int
      get() = content.id

    val occurrenceNavigatable: NavigatableId?
      get() = if (content.hasProblems && childCount == 0) content.navigatables.firstOrNull() else null

    fun addChild(node: MyNode): Boolean {
      assert(node.parent == null)
      node.parent = this
      if (children == null) {
        children = Vector()
      }
      children.add(node)
      clearCacheIfInvalid()
      if (node.isVisible()) {
        cachedVisibleChildren?.add(node)
        treeModel.nodesWereInserted(this, intArrayOf(childCount - 1))
        return true
      }
      return false
    }

    private fun updateContent(newContent: BuildTreeNode) {
      val parent = getParent()
      if (parent == null) {
        LOG.warn("Cannot update root node")
        return
      }
      val oldContent = content
      assert(newContent.id == oldContent.id)
      assert(newContent.parentId == oldContent.parentId)
      val nodeIndex = parent.getIndex(this)
      val wasVisible = nodeIndex >= 0
      userObject = newContent
      val visible = isVisible()
      if (visible) {
        if (wasVisible) {
          treeModel.nodesChanged(parent, intArrayOf(nodeIndex))
        }
        else {
          parent.clearCache()
          treeModel.nodesWereInserted(parent, intArrayOf(parent.getIndex(this)))
        }
      }
      else if (wasVisible) {
        parent.cachedVisibleChildren?.removeAt(nodeIndex)
        treeModel.nodesWereRemoved(parent, intArrayOf(nodeIndex), arrayOf(this))
      }
      else {
        parent.clearCacheIfInvalid()
      }
    }

    fun reload() {
      val state = TreeState.createOn(tree)
      treeModel.nodeStructureChanged(this)
      state.applyTo(tree)
    }

    fun removeChildren() {
      children = null
      clearCache()
      treeModel.nodeStructureChanged(this)
    }

    private fun clearCache() {
      cachedVisibleChildren = null
    }

    private fun clearCacheIfInvalid() {
      if (cachedFilteringState != filteringState) {
        cachedVisibleChildren = null
      }
    }

    private fun isVisible(): Boolean {
      return with(content) {
        visibleAlways ||
        visibleAsSuccessful && filteringState.showSuccessful ||
        visibleAsWarning && filteringState.showWarnings
      }
    }

    fun getVisibleChildren(): List<MyNode> {
      val allChildren = children ?: return emptyList()
      val cached = cachedVisibleChildren
      val currentFilteringState = filteringState
      if (cached != null && cachedFilteringState == currentFilteringState) {
        return cached
      }
      cachedFilteringState = currentFilteringState
      val result = mutableListOf<MyNode>()
      allChildren.forEach {
        if ((it as MyNode).isVisible()) {
          result.add(it)
        }
      }
      cachedVisibleChildren = result
      return result
    }

    override fun getChildAt(childIndex: Int): MyNode {
      return getVisibleChildren()[childIndex]
    }

    override fun getChildCount(): Int {
      return getVisibleChildren().size
    }

    override fun getParent(): MyNode? {
      return parent as? MyNode
    }

    override fun getIndex(node: TreeNode): Int {
      return if ((node as? MyNode)?.isVisible() == true) getVisibleChildren().indexOf(node) else -1
    }

    override fun children(): Enumeration<TreeNode> {
      return Collections.enumeration(getVisibleChildren())
    }

    override fun getNextNode(): MyNode? {
      return super.getNextNode() as? MyNode
    }

    override fun getPreviousNode(): MyNode? {
      return super.getPreviousNode() as? MyNode
    }

    override fun insert(newChild: MutableTreeNode?, childIndex: Int) {
      error("Unsupported")
    }

    override fun remove(childIndex: Int) {
      error("Unsupported")
    }

    override fun setParent(newParent: MutableTreeNode?) {
      error("Unsupported")
    }

    override fun setUserObject(userObject: Any?) {
      error("Unsupported")
    }

    // used by speed search
    override fun toString(): String {
      return with(content) {
        name ?: ((if (title.isNullOrEmpty()) "" else "${title}: ") + (hint ?: ""))
      }
    }
  }

  private class MyNodeRenderer : ColoredTreeCellRenderer() {
    private var myDurationText: String? = null
    private var myDurationColor: Color? = null
    private var myDurationWidth = 0
    private var myDurationLeftInset = 0
    private var myDurationRightInset = 0

    override fun customizeCellRenderer(
      tree: JTree,
      v: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      focused: Boolean,
    ) {
      val node = (v as? MyNode)?.content ?: return

      val icon = node.icon?.icon()
      setIcon(NodeRenderer.adjustIconInTree(icon, selected, focused))

      val title = node.title
      val name = node.name
      val hint = node.hint
      val hasTitle = !title.isNullOrEmpty()
      val hasName = !name.isNullOrEmpty()
      val hasHint = !hint.isNullOrEmpty()
      if (hasTitle) {
        append("$title: ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      }
      if (hasName && title != null || !hasTitle || hint != null) {
        append(name ?: "")
      }
      if (hasHint) {
        append(if (hasName) " $hint" else hint, SimpleTextAttributes.GRAY_ATTRIBUTES)
      }

      if (Registry.`is`("build.toolwindow.show.inline.statistics")) {
        val duration = node.duration
        if (duration != null) {
          myDurationText = duration
          val metrics = getFontMetrics(RelativeFont.SMALL.derive(getFont()))
          myDurationWidth = metrics.stringWidth(duration)
          myDurationLeftInset = metrics.height / 4
          myDurationRightInset = if (isNewUI()) tree.getInsets().right + JBUI.scale(4) else myDurationLeftInset
          myDurationColor = if (selected) UIUtil.getTreeSelectionForeground(focused) else SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
        }
        else {
          myDurationText = null
          myDurationColor = null
          myDurationWidth = 0
          myDurationLeftInset = 0
          myDurationRightInset = 0
        }
      }
    }

    override fun getPreferredSize(): Dimension {
      val preferredSize = super.getPreferredSize()
      if (myDurationWidth > 0) {
        preferredSize.width += myDurationWidth + myDurationLeftInset + myDurationRightInset
      }
      return preferredSize
    }

    override fun paintComponent(g: Graphics) {
      UISettings.setupAntialiasing(g)
      var width = getWidth()
      val height = getHeight()
      if (isOpaque) {
        // paint background for expanded row
        g.color = getBackground()
        g.fillRect(0, 0, width, height)
      }
      var clippedGraphics: Graphics? = null
      val duration = myDurationText
      if (duration != null && myDurationWidth > 0) {
        width -= myDurationWidth + myDurationLeftInset + myDurationRightInset
        if (width > 0 && height > 0) {
          g.color = myDurationColor
          g.font = RelativeFont.SMALL.derive(getFont())
          g.drawString(duration, width + myDurationLeftInset, getTextBaseLine(g.fontMetrics, height))
          clippedGraphics = g.create(0, 0, width, height)
        }
      }
      super.paintComponent(clippedGraphics ?: g)
      clippedGraphics?.dispose()
    }
  }
}