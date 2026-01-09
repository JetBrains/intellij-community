// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.*
import com.intellij.build.events.BuildEventsNls
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.AnsiEscapeDecoder.ColoredTextAcceptor
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.OccurenceNavigator.OccurenceInfo
import com.intellij.ide.ui.icons.icon
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.buildView.BuildViewApi
import com.intellij.ui.*
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.FocusEvent
import java.beans.PropertyChangeListener
import java.util.function.Function
import javax.swing.*
import javax.swing.event.ListSelectionListener
import kotlin.math.max

private val LOG = fileLogger()

internal class FrontendMultipleBuildsView(
  private val project: Project,
  buildContent: BuildContent,
  private val scope: CoroutineScope,
) : Disposable {
  private val buildContentManager = BuildContentManager.getInstance(project) as BuildContentManagerImpl
  private val id = buildContent.id
  private val name = buildContent.name
  private val buildMap = mutableMapOf<BuildId, FrontendBuildInfo>()
  private val viewMap = mutableMapOf<FrontendBuildInfo, FrontendBuildView>()
  private val toolbarActions = DefaultActionGroup()
  private val threeComponentsSplitter = createSplitter()
  private val buildListModel = DefaultListModel<FrontendBuildInfo>()
  private val buildList = createList()
  private val content: Content = createContent(id, buildContent.isPinned)
  private val focusWatcher = FocusWatcher()
  private var activeView: FrontendBuildView? = null
  private var locked = false

  init {
    updateBuildsListRowHeight()
    focusWatcher.install()
    buildContentManager.addContent(content)
  }

  override fun dispose() {
    log { "Disposed" }
    focusWatcher.uninstall()
  }

  private fun createSplitter() = OnePixelSplitter("MultipleBuildsView.Splitter.Proportion", 0.25f).also {
    if (isNewUI()) ScrollableContentBorder.setup(it, Side.LEFT)
  }

  private fun createList() = JBList(buildListModel).apply {
    installCellRenderer(MyCellRenderer())
    putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    addListSelectionListener(ListSelectionListener {
      val info = selectedValue
      log { "Selected $info" }
      setActiveView(viewMap[info])
    })
  }

  private fun createContentUI() : JComponent {
    val consoleComponent = MultipleBuildsPanel()
    consoleComponent.add(threeComponentsSplitter, BorderLayout.CENTER)
    val tb = ActionManager.getInstance().createActionToolbar("BuildView", toolbarActions, false)
    tb.targetComponent = consoleComponent
    if (!isNewUI()) {
      tb.component.border = JBUI.Borders.merge(tb.component.border,
                                               JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 0, 1),
                                               true)
    }
    consoleComponent.add(tb.component, BorderLayout.WEST)
    return consoleComponent
  }

  private fun createContent(contentId: BuildContentId, pinned: Boolean) : Content {
    log { "Creating content, pinned=$pinned" }
    val content = object : ContentImpl(createContentUI(), name, true) {
      override fun dispose() {
        log { "Content disposed" }
        super.dispose()
        Disposer.dispose(this@FrontendMultipleBuildsView)
        scope.launch {
          BuildViewApi.getInstance().disposeBuildContent(contentId)
        }
      }
    }
    if (pinned) {
      content.isPinned = true
    }
    content.addPropertyChangeListener(PropertyChangeListener { e ->
      if (e.propertyName == Content.PROP_PINNED) {
        val newPinned = e.newValue as Boolean
        log { "Pinned status changed to $newPinned" }
        scope.launch {
          BuildViewApi.getInstance().setBuildContentPinned(contentId, newPinned)
        }
      }
    })
    return content
  }

  fun lockContent() {
    val tabName: String = getLockedTabName()
    log { "Locking content as '$tabName'" }
    content.isPinned = true
    content.isPinnable = false
    if (content.icon == null) {
      content.icon = EmptyIcon.ICON_8
    }
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    buildContentManager.updateTabDisplayName(content, tabName)
    locked = true
  }

  private fun getLockedTabName(): @NlsContexts.TabTitle String {
    val buildInfo = viewMap.keys.minByOrNull { it.startTime }
    if (buildInfo != null) {
      val viewName = name.split(' ')[0]
      val titleWithoutPrefix = buildInfo.title.removePrefix(viewName)
      val tabName = "$viewName: $titleWithoutPrefix"
      if (viewMap.size > 1) {
        return LangBundle.message("tab.title.more", tabName, viewMap.size - 1)
      }
      return tabName
    }
    return name
  }

  private fun updateBuildsListRowHeight() {
    buildList.setFixedCellHeight(JBUI.scale(UIUtil.LIST_FIXED_CELL_HEIGHT * 2))
  }

  private fun setActiveView(view: FrontendBuildView?) {
    if (activeView === view) {
      return
    }
    activeView = view
    configureToolbar(view)
    if (view == null) {
      threeComponentsSplitter.secondComponent = null
      content.preferredFocusableComponent = null
    }
    else {
      threeComponentsSplitter.secondComponent = view
      content.preferredFocusableComponent = view
      if (focusWatcher.focused) {
        view.requestFocusInWindow() // todo check IJPL-172285
      }
    }
  }

  private fun configureToolbar(view: FrontendBuildView?) {
    toolbarActions.removeAll()
    if (view != null) {
      // todo view-dependent actions
      toolbarActions.add(PinBuildViewAction())
      toolbarActions.add(view.createFilteringActionGroup())
    }
  }

  fun handleEvent(event: BuildViewEvent) {
    when (event) {
      is BuildViewEvent.BuildStarted -> {
        val info = FrontendBuildInfo(event)
        buildMap[event.buildId] = info
        buildListModel.addElement(info)
        val buildView = FrontendBuildView(project, scope, event.treeViewId, event.consoleComponent)
        viewMap[info] = buildView
        Disposer.register(this, buildView)
        if (activeView == null) {
          log { "Selecting $info" }
          setActiveView(buildView)
        }
        if (buildListModel.size == 2) {
          buildList.selectedIndex = 0
          setBuildListVisible(true)
        }
        buildContentManager.startBuildNotified(info, content, null /* todo support termination check */)
        buildContentManager.setSelectedContent(content, event.requestFocus, event.requestFocus, event.activateToolWindow) {
          scope.launch {
            log { "notifyTooWindowActivated($info)" }
            BuildViewApi.getInstance().notifyTooWindowActivated(event.buildId)
          }
        }
      }
      is BuildViewEvent.BuildSelected -> {
        val info = buildMap[event.buildId] ?: return
        buildList.setSelectedValue(info, false)
      }
      is BuildViewEvent.BuildStatusChanged -> {
        val info = buildMap[event.buildId] ?: return
        info.statusMessage = event.message
        refreshBuildList()
      }
      is BuildViewEvent.BuildFinished -> {
        val info = buildMap[event.buildId] ?: return
        info.message = event.message
        info.icon = event.iconId.icon()
        refreshBuildList()
        buildContentManager.finishBuildNotified(info, content)
        if (event.selectContent) {
          buildContentManager.setSelectedContent(content, false, false, event.activateToolWindow, null)
        }
        event.notification?.let {
          SystemNotifications.getInstance().notify(UIBundle.message("tool.window.name.build"), it.title, it.content)
        }
      }
      is BuildViewEvent.BuildRemoved -> {
        val info = buildMap.remove(event.buildId) ?: return
        buildListModel.removeElement(info)
        val buildView = viewMap.remove(info)!!
        Disposer.dispose(buildView)
        if (buildListModel.size == 1) {
          setBuildListVisible(false)
        }
        if (buildView === activeView) {
          setActiveView(null)
        }
      }
    }
  }

  private fun setBuildListVisible(visible: Boolean) {
    if (visible) {
      log { "Showing build list" }
      threeComponentsSplitter.firstComponent = JBScrollPane(buildList).apply { border = JBUI.Borders.empty() }
    }
    else {
      log { "Hiding build list" }
      threeComponentsSplitter.firstComponent = null
    }
  }

  private fun refreshBuildList() {
    threeComponentsSplitter.firstComponent?.run {
      revalidate()
      repaint()
    }
  }

  private fun log(messageProvider: () -> String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("[$id/$name] ${messageProvider()}")
    }
  }

  private inner class FocusWatcher : AWTEventListener {
    var focused = false

    fun install() {
      Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.FOCUS_EVENT_MASK)
    }

    fun uninstall() {
      Toolkit.getDefaultToolkit().removeAWTEventListener(this)
    }

    override fun eventDispatched(event: AWTEvent) {
      if (event.id == FocusEvent.FOCUS_GAINED) {
        val oldFocused = focused
        focused = SwingUtilities.isDescendingFrom((event as ComponentEvent).component, content.getComponent())
        if (focused != oldFocused) {
          log { "Focused status changed to $focused" }
        }
      }
    }
  }

  private inner class MultipleBuildsPanel : JPanel(BorderLayout()), OccurenceNavigator {
    private fun getOccurenceNavigator(next: Boolean): Pair<Int, () -> OccurenceInfo?>? {
      if (buildListModel.size() == 0) return null
      val index = max(buildList.selectedIndex, 0)
      val range = if (next) index..<buildListModel.size() else index downTo 0
      return range.firstNotNullOfOrNull { i ->
        val buildInfo = buildListModel.getElementAt(i)
        val buildView: FrontendBuildView? = viewMap[buildInfo]
        if (buildView == null) return@firstNotNullOfOrNull null
        if (i != index) {
          val eventView = buildView.treeView
          if (eventView == null) return@firstNotNullOfOrNull null
          eventView.clearTreeSelection()
        }
        if (next) {
          if (buildView.hasNextOccurence()) return@firstNotNullOfOrNull i to buildView::goNextOccurence
        }
        else {
          if (buildView.hasPreviousOccurence()) {
            return@firstNotNullOfOrNull i to buildView::goPreviousOccurence
          }
          else if (i != index && buildView.hasNextOccurence()) {
            return@firstNotNullOfOrNull i to buildView::goNextOccurence
          }
        }
        null
      }
    }

    private fun go(next: Boolean): OccurenceInfo? {
      val navigator = getOccurenceNavigator(next)
      if (navigator != null) {
        buildList.selectedIndex = navigator.first
        return navigator.second()
      }
      return null
    }

    override fun hasNextOccurence() = getOccurenceNavigator(true) != null

    override fun hasPreviousOccurence() = getOccurenceNavigator(false) != null

    override fun goNextOccurence() = go(true)

    override fun goPreviousOccurence() = go(false)

    override fun getNextOccurenceActionName() = IdeBundle.message("action.next.problem")

    override fun getPreviousOccurenceActionName() = IdeBundle.message("action.previous.problem")

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun updateUI() {
      super.updateUI()
      updateBuildsListRowHeight()
    }
  }

  private class MyCellRenderer : Function<FrontendBuildInfo, JComponent> {
    private val ansiEscapeDecoder = AnsiEscapeDecoder()

    override fun apply(obj: FrontendBuildInfo): JComponent {
      val panel = JPanel(BorderLayout())

      val mainComponent = SimpleColoredComponent().apply {
        setIcon(obj.icon)
        append(obj.title + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        append(obj.message, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
      panel.add(mainComponent, BorderLayout.NORTH)

      obj.statusMessage?.let { statusMessage ->
        val statusComponent = SimpleColoredComponent().apply {
          setIcon(EmptyIcon.ICON_16)
          ansiEscapeDecoder.escapeText(statusMessage, ProcessOutputTypes.STDOUT, ColoredTextAcceptor { text, _ ->
            append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES) //NON-NLS
          })
        }
        panel.add(statusComponent, BorderLayout.SOUTH)
      }

      return panel
    }
  }

  private inner class PinBuildViewAction : DumbAwareAction(), Toggleable {
    override fun actionPerformed(e: AnActionEvent) {
      val selected: Boolean = !content.isPinned
      if (selected) {
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
      }
      content.isPinned = selected
      Toggleable.setSelected(e.presentation, selected)
      log { "PinBuildViewAction: $selected" }
    }

    override fun update(e: AnActionEvent) {
      if (!content.isValid) return

      if (locked) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      val selected = content.isPinned

      e.presentation.icon = AllIcons.General.Pin_tab
      Toggleable.setSelected(e.presentation, selected)
      e.presentation.text = IdeBundle.message(if (selected) "action.unpin.tab" else "action.pin.tab")
      e.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  private class FrontendBuildInfo(event: BuildViewEvent.BuildStarted) : BuildDescriptor {
    private val id = event.buildId
    private val title = event.title
    private val startTime = event.startTime
    var icon: Icon = AnimatedIcon.Default.INSTANCE
    var message = event.message
    var statusMessage : @BuildEventsNls.Message String? = null

    override fun getId() = id
    override fun getTitle() = title
    override fun getWorkingDir() = ""
    override fun getStartTime() = startTime

    override fun toString() = "Build[$id/$title]"
  }
}