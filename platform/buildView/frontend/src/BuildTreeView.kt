// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.*
import com.intellij.ide.IdeBundle
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.OccurenceNavigatorSupport
import com.intellij.ide.actions.OccurenceNavigatorActionBase
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.rpc.NavigatableId
import com.intellij.ide.rpc.navigatable
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.ui.*
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.tree.TreePathUtil
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.disposeOnCompletion
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
import javax.swing.Timer
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.*

private val LOG = fileLogger()

internal class BuildTreeView(
  private val project: Project,
  parentScope: CoroutineScope,
  private val buildViewId: BuildViewId,
  private val backendNavigationAndFiltering: Boolean = true,
) : JPanel(), UiDataProvider, ComponentContainer {
  private val uiScope = parentScope.childScope("BuildTreeView", Dispatchers.UI + ModalityState.any().asContextElement())
  private val model = BuildTreeViewModelProxy.getInstance(buildViewId)

  private val rootNode = MyNode(
    BuildTreeNode(BuildTreeNode.ROOT_ID, BuildTreeNode.NO_ID,
                  null, null, "", null, null, emptyList(), false, false, false, false, false))
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

  internal var showingSuccessful: Boolean
    get() = filteringState.showSuccessful
    set(value) {
      handleFilteringStateChange(filteringState.copy(showSuccessful = value))
    }

  internal var showingWarnings: Boolean
    get() = filteringState.showWarnings
    set(value) {
      handleFilteringStateChange(filteringState.copy(showWarnings = value))
    }

  // A factor which can correct for the difference between frontend's and backend's clocks,
  // in case we need to display a duration of a process, for which we know the start timestamp on the backend.
  // It doesn't include the connection latency, but that seems acceptable in our case.
  private var timeDiff = 0L

  private val durationUpdater = DurationUpdater().also {
    Disposer.register(this, it)
  }

  init {
    LOG.debug { "Creating BuildTreeView(id=$buildViewId)" }
    this.disposeOnCompletion(uiScope)
    uiScope.launch {
      val nodeMap = mutableMapOf(buildProgressRootNode.id to buildProgressRootNode)
      model.getTreeEventsFlow().collect { event ->
        handleTreeEvent(event, nodeMap)
      }
    }
    if (backendNavigationAndFiltering) {
      uiScope.launch(Dispatchers.EDT /* Navigatable-s might expect WIL to be taken */) {
        model.getNavigationFlow().collect {
          handleNavigation(it.forward)
        }
      }
      uiScope.launch {
        navigationContext.collect {
          LOG.debug { "Navigation context: $it" }
          model.onNavigationContextChange(it)
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
        timeDiff = event.currentTimestamp - System.currentTimeMillis()
        val nodeInfos = event.nodes
        var needsNavigationContextUpdate = false
        if (nodeInfos.isEmpty()) {
          LOG.debug("Clearing nodes")
          durationUpdater.reset()
          nodeMap.clear()
          rootNode.removeChildren()
          needsNavigationContextUpdate = true
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
              durationUpdater.onNodeAdded(nodeInfo)
              val newNode = MyNode(nodeInfo)
              nodeMap[nodeInfo.id] = newNode
              if (parentNode.addChild(newNode)) {
                maybeExpand(TreePathUtil.toTreePath(parentNode))
                needsNavigationContextUpdate = needsNavigationContextUpdate ||
                                               parentNode.childCount == 1 || // adding first child might change parent's navigatable status
                                               newNode.occurrenceNavigatable != null
              }
            }
            else {
              LOG.debug { "Updating node (id=${nodeInfo.id})" }
              val wasNavigatable = node.isNavigatable
              durationUpdater.onNodeUpdated(node.content, nodeInfo)
              node.content = nodeInfo
              needsNavigationContextUpdate = needsNavigationContextUpdate || node.childCount != 0 || node.isNavigatable != wasNavigatable
            }
          }
        }
        if (needsNavigationContextUpdate) {
          updateNavigationContext()
        }
      }
      is BuildTreeExposeRequest -> {
        val nodeId = event.nodeId
        if (nodeId == null) {
          LOG.debug { "Clearing selection" }
          clearTreeSelection()
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
      is BuildTreeFilteringState -> {
        if (backendNavigationAndFiltering) {
          handleFilteringStateChange(event)
        }
      }
    }
  }

  private fun handleFilteringStateChange(filteringState: BuildTreeFilteringState) {
    if (filteringState == this.filteringState) {
      LOG.debug { "No-op filtering state update, already set to $filteringState" }
    }
    else {
      LOG.debug { "Filtering state update: $filteringState" }
      val treeWasEmpty = buildProgressRootNode.childCount == 0
      this.filteringState = filteringState
      rootNode.reload()
      if (treeWasEmpty) {
        tree.expandRow(0)
      }
      updateNavigationContext()
    }
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

      OccurenceNavigatorActionBase.displayOccurrencesInfoInStatusBar(project, info.occurenceNumber, info.occurencesCount)
    }
  }

  private fun onTreeSelectionChanged(e: TreeSelectionEvent) {
    if (e.path != null) {
      val selectedNode = tree.selectionPath?.lastPathComponent as? MyNode
      val selectedNodeId = selectedNode?.id
      uiScope.launch {
        LOG.debug { "Selection change: $selectedNodeId" }
        model.onSelectionChange(selectedNodeId)
      }
      updateNavigationContext()
    }
  }

  private fun updateNavigationContext() {
    if (backendNavigationAndFiltering) {
      val hasPrevNode = occurenceNavigatorSupport.hasPreviousOccurence()
      val hasNextNode = occurenceNavigatorSupport.hasNextOccurence()
      val hasAnyNode = hasNextNode || hasPrevNode || (tree.selectionPath?.lastPathComponent as? MyNode)?.occurrenceNavigatable != null
      navigationContext.value = BuildTreeNavigationContext(hasPrevNode, hasNextNode, hasAnyNode)
    }
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

  internal fun clearTreeSelection() {
    tree.clearSelection()
  }

  internal fun hasNextOccurence(): Boolean {
    return occurenceNavigatorSupport.hasNextOccurence()
  }

  internal fun hasPreviousOccurence(): Boolean {
    return occurenceNavigatorSupport.hasPreviousOccurence()
  }

  internal fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? {
    return occurenceNavigatorSupport.goNextOccurence()
  }

  internal fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? {
    return occurenceNavigatorSupport.goPreviousOccurence()
  }

  private class MyOccurenceNavigatorSupport(tree: JTree) : OccurenceNavigatorSupport(tree) {
    override fun createDescriptorForNode(node: DefaultMutableTreeNode): Navigatable? {
        return (node as? MyNode)?.occurrenceNavigatable?.navigatable()
    }

    override fun getNextOccurenceActionName() = ""
    override fun getPreviousOccurenceActionName() = ""
  }

  private inner class MyNode(content: BuildTreeNode) : DefaultMutableTreeNode(content), PathElementIdProvider {
    val id = content.id
    val elementId = content.id.toString()

    var content: BuildTreeNode
      get() = userObject as BuildTreeNode
      set(newContent) {
        updateContent(newContent)
      }

    private var cachedVisibleChildren: MutableList<MyNode>? = null
    private var cachedIndex = -1
    private var cachedFilteringState: BuildTreeFilteringState? = null

    val occurrenceNavigatable: NavigatableId?
      get() = if (content.hasProblems && childCount == 0) content.navigatables.firstOrNull() else null

    val isNavigatable: Boolean
      get() = isVisible() && occurrenceNavigatable != null

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
        val newIndex = childCount - 1
        node.cachedIndex = newIndex
        treeModel.nodesWereInserted(this, intArrayOf(newIndex))
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
        parent.clearCache()
        treeModel.nodesWereRemoved(parent, intArrayOf(nodeIndex), arrayOf(this))
      }
      else {
        parent.clearCacheIfInvalid()
      }
    }

    fun reload() {
      val state = TreeState.createOn(tree, true, true)
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
      cachedFilteringState = null
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
      if (children == null) return emptyList()
      val cached = cachedVisibleChildren
      if (cached != null && cachedFilteringState == filteringState) {
        return cached
      }
      return rebuildCache()
    }

    private fun rebuildCache(): List<MyNode> {
      cachedFilteringState = filteringState
      val result = mutableListOf<MyNode>()
      var index = 0
      children.forEach {
        val child = it as MyNode
        if (child.isVisible()) {
          result.add(it)
          child.cachedIndex = index++
        }
        else {
          child.cachedIndex = -1
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
      if (node !is MyNode || children == null || node.parent !== this) return -1
      if (cachedFilteringState != filteringState) {
        rebuildCache()
      }
      return node.cachedIndex
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

    // required for correct TreeState operation in case there are nodes with identical presentation
    override fun getPathElementId(): String {
      return elementId
    }
  }

  private inner class MyNodeRenderer : ColoredTreeCellRenderer() {
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
        val duration = node.duration?.getPresentation()
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

    private fun BuildDuration.getPresentation() =
      when(this) {
        is BuildDuration.Fixed -> NlsMessages.formatDuration(durationMs)
        is BuildDuration.InProgress -> {
          var duration = System.currentTimeMillis() - startTimestamp + timeDiff
          if (duration > 1000) {
            duration -= duration % 1000
          }
          NlsMessages.formatDurationApproximate(duration)
        }
      }
  }

  private inner class DurationUpdater : Disposable {
    private var nodesInProgress = 0

    private val repaintTimer = Timer(1000) {
      tree.repaint()
    }

    fun reset() {
      nodesInProgress = 0
      repaintTimer.stop()
    }

    fun onNodeAdded(node: BuildTreeNode) {
      if (node.isInProgress()) {
        if (nodesInProgress++ == 0) {
          repaintTimer.start()
        }
      }
    }

    fun onNodeUpdated(before: BuildTreeNode, after: BuildTreeNode) {
      if (before.isInProgress() != after.isInProgress()) {
        if (before.isInProgress()) {
          if (--nodesInProgress == 0) {
            repaintTimer.stop()
          }
        }
        else {
          if (nodesInProgress++ == 0) {
            repaintTimer.start()
          }
        }
      }
    }

    private fun BuildTreeNode.isInProgress() = duration is BuildDuration.InProgress

    override fun dispose() {
      repaintTimer.stop()
    }
  }
}