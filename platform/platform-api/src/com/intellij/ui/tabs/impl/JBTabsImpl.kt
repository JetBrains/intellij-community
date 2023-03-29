// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "LeakingThis")

package com.intellij.ui.tabs.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.fill2DRoundRect
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.ui.ShadowAction
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.*
import com.intellij.openapi.util.IconLoader.getTransparentIcon
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBThinOverlappingScrollBar
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.hover.HoverListener
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.ui.tabs.*
import com.intellij.ui.tabs.JBTabPainter.Companion.DEFAULT
import com.intellij.ui.tabs.UiDecorator.UiDecoration
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout
import com.intellij.ui.tabs.impl.singleRow.SingleRowPassInfo
import com.intellij.ui.tabs.impl.table.TableLayout
import com.intellij.ui.tabs.impl.themes.TabTheme
import com.intellij.util.Alarm
import com.intellij.util.Function
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.StartupUiUtil.addAwtListener
import com.intellij.util.ui.update.LazyUiDisposable
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import javax.accessibility.*
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.ChangeListener
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.ComponentUI
import kotlin.Pair

private val ABC_COMPARATOR: Comparator<TabInfo> = Comparator { o1, o2 -> NaturalComparator.INSTANCE.compare(o1.text, o2.text) }
private val LOG = logger<JBTabsImpl>()
private const val SCROLL_BAR_THICKNESS = 3

@DirtyUI
open class JBTabsImpl(private var myProject: Project?,
                      parentDisposable: Disposable) : JComponent(), JBTabsEx, PropertyChangeListener, TimerListener, DataProvider,
                                                      PopupMenuListener, JBTabsPresentation, Queryable, UISettingsListener,
                                                      QuickActionProvider, MorePopupAware, Accessible {
  companion object {
    @JvmField
    val PINNED = Key.create<Boolean>("pinned")

    @JvmField
    val SIDE_TABS_SIZE_LIMIT_KEY = Key.create<Int>("SIDE_TABS_SIZE_LIMIT_KEY")

    private val HIDDEN_INFOS_SELECT_INDEX_KEY = Key.create<Int>("HIDDEN_INFOS_SELECT_INDEX")

    @JvmField
    val MIN_TAB_WIDTH = scale(75)

    @JvmField
    val DEFAULT_MAX_TAB_WIDTH = scale(300)

    @JvmField
    val defaultDecorator: UiDecorator = DefaultDecorator()

    private const val adjustBorders = true
    private const val LAYOUT_DONE: @NonNls String = "Layout.done"

    @JvmStatic
    fun getComponentImage(info: TabInfo): Image {
      val cmp = info.component
      val img: BufferedImage
      if (cmp.isShowing) {
        val width = cmp.width
        val height = cmp.height
        img = UIUtil.createImage(info.component, if (width > 0) width else 500, if (height > 0) height else 500,
                                 BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        cmp.paint(g)
      }
      else {
        img = UIUtil.createImage(info.component, 500, 500, BufferedImage.TYPE_INT_ARGB)
      }
      return img
    }

    @JvmStatic
    val selectionTabVShift: Int
      get() = 2

    private fun isToDeferRemoveForLater(c: JComponent): Boolean {
      return c.rootPane != null
    }

    private fun isChanged(oldObject: Any?, newObject: Any?): Boolean {
      return !Comparing.equal(oldObject, newObject)
    }

    @JvmStatic
    fun isSelectionClick(e: MouseEvent): Boolean {
      if (e.clickCount == 1) {
        if (!e.isPopupTrigger) {
          return e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isAltDown && !e.isMetaDown
        }
      }
      return false
    }

    @JvmStatic
    fun resetLayout(c: JComponent?) {
      if (c == null) {
        return
      }
      c.putClientProperty(LAYOUT_DONE, null)
    }
  }

  private val myVisibleInfos: MutableList<TabInfo> = ArrayList()
  private val infoToPage: MutableMap<TabInfo, AccessibleTabPage> = HashMap()
  private val myHiddenInfos: MutableMap<TabInfo, Int> = HashMap()
  private var mySelectedInfo: TabInfo? = null

  val infoToLabel: MutableMap<TabInfo, TabLabel> = HashMap()
  val infoToToolbar: MutableMap<TabInfo, Toolbar> = HashMap()

  val moreToolbar: ActionToolbar?
  var entryPointToolbar: ActionToolbar? = null
  val titleWrapper = NonOpaquePanel()

  var headerFitSize: Dimension? = null

  private var innerInsets: Insets = JBInsets.emptyInsets()
  private val myTabMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList<EventListener>()
  private val myTabListeners = ContainerUtil.createLockFreeCopyOnWriteList<TabsListener>()
  private var myFocused = false
  private var myPopupGroup: Supplier<out ActionGroup>? = null
  var popupPlace: String? = null
    private set
  var popupInfo: TabInfo? = null
  val myNavigationActions: DefaultActionGroup
  val popupListener: PopupMenuListener
  var activePopup: JPopupMenu? = null
  var horizontalSide = true
  var isSideComponentOnTabs = true
    private set
  var isSideComponentBefore = true
    private set
  val separatorWidth = JBUI.scale(1)
  private var dataProvider: DataProvider? = null
  private val myDeferredToRemove = WeakHashMap<Component, Component>()
  private var singleRowLayout: SingleRowLayout
  private var tableLayout = createTableLayout()

  // it's an invisible splitter intended for changing size of tab zone
  private val mySplitter = TabsSideSplitter(this)
  var effectiveLayout: TabLayout? = null
    private set
  var lastLayoutPass: LayoutPassInfo? = null
    private set

  internal var forcedRelayout: Boolean = false
    private set

  private var uiDecorator: UiDecorator? = null
  private var myPaintFocus = false
  private var myHideTabs = false
  private var myHideTopPanel = false
  private var isRequestFocusOnLastFocusedComponent = false
  private var listenerAdded = false
  val attractions: MutableSet<TabInfo> = HashSet()
  private val animator: Animator
  private var allTabs: List<TabInfo>? = null
  private var focusManager: IdeFocusManager = IdeFocusManager.getGlobalInstance()
  private val nestedTabs: MutableSet<JBTabsImpl> = HashSet()
  var addNavigationGroup: Boolean = true
  private var myActiveTabFillIn: Color? = null
  private var myTabLabelActionsAutoHide = false
  private val tabActionsAutoHideListener = TabActionsAutoHideListener()
  private var tabActionsAutoHideListenerDisposable = Disposer.newDisposable()
  private var glassPane: IdeGlassPane? = null

  internal var tabActionsMouseDeadZone: TimedDeadzone.Length = TimedDeadzone.DEFAULT
    private set

  private var myRemoveDeferredRequest: Long = 0
  var position = JBTabsPosition.top
    private set
  private val myBorder = createTabBorder()
  private val myNextAction: BaseNavigationAction?
  private val myPrevAction: BaseNavigationAction?
  var isTabDraggingEnabled = false
    private set
  protected var dragHelper: DragHelper? = null
  private var navigationActionsEnabled = true
  protected var dropInfo: TabInfo? = null

  override var dropInfoIndex = 0

  override var dropSide = -1
  protected var showDropLocation: Boolean = true
  private var myOldSelection: TabInfo? = null
  private var mySelectionChangeHandler: JBTabs.SelectionChangeHandler? = null
  private var deferredFocusRequest: Runnable? = null
  private var firstTabOffset = 0
  val tabPainterAdapter = createTabPainterAdapter()
  val tabPainter = tabPainterAdapter.tabPainter

  private var alphabeticalMode = false
  open fun isAlphabeticalMode(): Boolean = alphabeticalMode

  private var supportsCompression = false
  private var emptyText: String? = null

  var isMouseInsideTabsArea = false
    private set

  private var removeNotifyInProgress = false
  private var singleRow = true
  protected open fun createTabBorder(): JBTabsBorder {
    return JBDefaultTabsBorder(this)
  }

  protected open fun createTabPainterAdapter(): TabPainterAdapter = DefaultTabPainterAdapter(DEFAULT)

  private var tabLabelAtMouse: TabLabel? = null
  private val scrollBar: JBScrollBar
  private val myScrollBarChangeListener: ChangeListener
  private var scrollBarOn = false

  constructor(project: Project) : this(project, project)

  init {
    isOpaque = true
    background = tabPainter.getBackgroundColor()
    border = myBorder
    myNavigationActions = DefaultActionGroup()
    myNextAction = SelectNextAction(this, parentDisposable)
    myPrevAction = SelectPreviousAction(this, parentDisposable)
    myNavigationActions.add(myNextAction)
    myNavigationActions.add(myPrevAction)
    setUiDecorator(null)
    singleRowLayout = createSingleRowLayout()
    setLayout(singleRowLayout)
    mySplitter.divider.isOpaque = false
    popupListener = object : PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}
      override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
        disposePopupListener()
      }

      override fun popupMenuCanceled(e: PopupMenuEvent) {
        disposePopupListener()
      }
    }
    val actionManager = ActionManager.getInstance()
    moreToolbar = createToolbar(DefaultActionGroup(actionManager.getAction("TabList")))
    add(moreToolbar.component)
    val entryPointActionGroup = entryPointActionGroup
    if (entryPointActionGroup == null) {
      entryPointToolbar = null
    }
    else {
      entryPointToolbar = createToolbar(entryPointActionGroup)
      add(entryPointToolbar!!.component)
    }
    add(titleWrapper)
    Disposer.register(parentDisposable) { setTitleProducer(null) }

    // This scroll pane won't be shown on screen, it is needed only to handle scrolling events and properly update a scrolling model
    val fakeScrollPane = JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS)
    scrollBar = JBThinOverlappingScrollBar(if (isHorizontalTabs) Adjustable.HORIZONTAL else Adjustable.VERTICAL)
    fakeScrollPane.verticalScrollBar = scrollBar
    fakeScrollPane.horizontalScrollBar = scrollBar
    fakeScrollPane.isVisible = true
    fakeScrollPane.setBounds(0, 0, 0, 0)
    add(scrollBar)
    addMouseWheelListener { event ->
      val modifiers = UIUtil.getAllModifiers(event) or if (isHorizontalTabs) InputEvent.SHIFT_DOWN_MASK else 0
      val e = MouseEventAdapter.convert(event, fakeScrollPane, event.id, event.getWhen(), modifiers, event.x, event.y)
      MouseEventAdapter.redispatch(e, fakeScrollPane)
    }
    val listener = object : AWTEventListener {
      val afterScroll = Alarm(parentDisposable)
      override fun eventDispatched(event: AWTEvent) {
        var tabRectangle: Rectangle? = null
        if (singleRowLayout.myLastSingRowLayout != null) {
          tabRectangle = singleRowLayout.myLastSingRowLayout.tabRectangle
        }
        else if (tableLayout.myLastTableLayout != null) {
          tabRectangle = tableLayout.myLastTableLayout.tabRectangle
        }
        if (tabRectangle == null) return
        val me = event as MouseEvent
        val point = me.point
        SwingUtilities.convertPointToScreen(point, me.component)
        var rect = visibleRect
        rect = rect.intersection(tabRectangle)
        val p = rect.location
        SwingUtilities.convertPointToScreen(p, this@JBTabsImpl)
        rect.location = p
        val inside = rect.contains(point)
        if (inside != isMouseInsideTabsArea) {
          isMouseInsideTabsArea = inside
          afterScroll.cancelAllRequests()
          if (!inside) {
            afterScroll.addRequest({
                                     // here is no any "isEDT"-checks <== this task should be called in EDT <==
                                     // <== Alarm instance executes tasks in EDT <== used constructor of Alarm uses EDT for tasks by default
                                     if (!isMouseInsideTabsArea) {
                                       relayout(false, false)
                                     }
                                   }, 500)
          }
        }
      }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_MOTION_EVENT_MASK)
    Disposer.register(parentDisposable) {
      Toolkit.getDefaultToolkit()?.removeAWTEventListener(listener)
    }
    animator = object : Animator("JBTabs Attractions", 2, 500, true) {
      override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
        repaintAttractions()
      }
    }
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getDefaultComponent(aContainer: Container): Component? = toFocus
    }
    val listener1 = object : LazyUiDisposable<JBTabsImpl>(parentDisposable, this, this) {
      override fun initialize(parent: Disposable, child: JBTabsImpl, project: Project?) {
        if (myProject == null && project != null) {
          myProject = project
        }
        Disposer.register(parentDisposable, animator)
        Disposer.register(parentDisposable) { removeTimerUpdate() }
        val gp = IdeGlassPaneUtil.find(child)
        tabActionsAutoHideListenerDisposable = Disposer.newDisposable("myTabActionsAutoHideListener")
        Disposer.register(parentDisposable, tabActionsAutoHideListenerDisposable)
        gp.addMouseMotionPreprocessor(tabActionsAutoHideListener, tabActionsAutoHideListenerDisposable)
        glassPane = gp
        addAwtListener({
                         if (!JBPopupFactory.getInstance().getChildPopups(this@JBTabsImpl).isEmpty()) return@addAwtListener
                         processFocusChange()
                       }, AWTEvent.FOCUS_EVENT_MASK, parentDisposable)
        dragHelper = createDragHelper(child, parentDisposable)
        dragHelper!!.start()
        if (myProject != null && this@JBTabsImpl.focusManager === IdeFocusManager.getGlobalInstance()) {
          this@JBTabsImpl.focusManager = IdeFocusManager.getInstance(myProject)
        }
      }
    }
    listener1.setupListeners()
    putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, visibleInfos.asSequence().filter { it != mySelectedInfo }.map { it.component })
    val hoverListener = object : HoverListener() {
      override fun mouseEntered(component: Component, x: Int, y: Int) {
        toggleScrollBar(isInsideTabsArea(x, y))
      }

      override fun mouseMoved(component: Component, x: Int, y: Int) {
        toggleScrollBar(isInsideTabsArea(x, y))
      }

      override fun mouseExited(component: Component) {
        toggleScrollBar(false)
      }
    }
    hoverListener.addTo(this)
    myScrollBarChangeListener = ChangeListener { updateTabsOffsetFromScrollBar() }
  }

  private fun isInsideTabsArea(x: Int, y: Int): Boolean {
    var area = headerFitSize
    if (tableLayout.myLastTableLayout != null) {
      area = tableLayout.myLastTableLayout.tabRectangle.size
    }
    if (area == null) {
      return false
    }
    return when (tabsPosition) {
      JBTabsPosition.top -> y <= area.height
      JBTabsPosition.left -> x <= area.width
      JBTabsPosition.bottom -> y >= height - area.height
      JBTabsPosition.right -> x >= width - area.width
    }
  }

  private fun toggleScrollBar(isOn: Boolean) {
    if (isOn == scrollBarOn) {
      return
    }

    scrollBarOn = isOn
    scrollBar.toggle(isOn)
  }

  private fun createToolbar(group: ActionGroup): ActionToolbar {
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TABS_MORE_TOOLBAR, group, true)
    toolbar.targetComponent = this
    toolbar.component.border = JBUI.Borders.empty()
    toolbar.component.isOpaque = false
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    return toolbar
  }

  protected open val entryPointActionGroup: DefaultActionGroup?
    get() = null

  private val scrollBarBounds: Rectangle
    get() = if (!isWithScrollBar || isHideTabs) Rectangle(0, 0, 0, 0)
    else when (tabsPosition) {
      JBTabsPosition.left -> {
        if (ExperimentalUI.isNewUI()) {
          val tabsRect = lastLayoutPass!!.headerRectangle
          if (tabsRect != null) {
            Rectangle(tabsRect.x + tabsRect.width - SCROLL_BAR_THICKNESS, 0,
                      SCROLL_BAR_THICKNESS, height)
          }
          else {
            Rectangle(0, 0, 0, 0)
          }
        }
        else {
          Rectangle(0, 0, SCROLL_BAR_THICKNESS, height)
        }
      }
      JBTabsPosition.right -> Rectangle(width - SCROLL_BAR_THICKNESS, 0,
                                        SCROLL_BAR_THICKNESS, height)
      JBTabsPosition.top -> Rectangle(0, 1, width, SCROLL_BAR_THICKNESS)
      JBTabsPosition.bottom -> Rectangle(0, height - SCROLL_BAR_THICKNESS, width,
                                         SCROLL_BAR_THICKNESS)
    }
  private val scrollBarModel: BoundedRangeModel
    get() = scrollBar.model
  val isWithScrollBar: Boolean
    get() = effectiveLayout!!.isWithScrollBar

  protected open fun createDragHelper(tabs: JBTabsImpl, parentDisposable: Disposable): DragHelper {
    return DragHelper(tabs, parentDisposable)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    for ((info, label) in infoToLabel) {
      info.revalidate()
      label.setTabActions(info.tabLabelActions)
    }
    updateRowLayout()
  }

  private fun updateRowLayout() {
    if (tabsPosition != JBTabsPosition.top) {
      singleRow = true
    }
    val forceTableLayout = (tabsPosition == JBTabsPosition.top && supportsTableLayoutAsSingleRow() && TabLayout.showPinnedTabsSeparately())
    val useTableLayout = !isSingleRow || forceTableLayout
    if (useTableLayout) {
      tableLayout = createTableLayout()
    }
    else {
      singleRowLayout = createSingleRowLayout()
    }
    val layout = if (useTableLayout) tableLayout else singleRowLayout
    layout.scroll(scrollBarModel.value) // set current scroll value to new layout
    setLayout(layout)
    relayout(forced = true, layoutNow = true)
  }

  protected open fun supportsTableLayoutAsSingleRow(): Boolean = false

  protected fun createTableLayout(): TableLayout {
    val isWithScrollBar = ((ExperimentalUI.isEditorTabsWithScrollBar()
                            && isSingleRow && tabsPosition == JBTabsPosition.top) && supportsTableLayoutAsSingleRow()
                           && TabLayout.showPinnedTabsSeparately()
                           && (!supportsCompression() || getInstance().hideTabsIfNeeded))
    return TableLayout(this, isWithScrollBar)
  }

  protected open fun createSingleRowLayout(): SingleRowLayout = ScrollableSingleRowLayout(this)

  override fun setNavigationActionBinding(prevActionId: String, nextActionId: String): JBTabs {
    myNextAction?.reconnect(nextActionId)
    myPrevAction?.reconnect(prevActionId)
    return this
  }

  fun setHovered(label: TabLabel?) {
    val old = tabLabelAtMouse
    tabLabelAtMouse = label
    if (old != null) {
      old.revalidate()
      old.repaint()
    }
    if (tabLabelAtMouse != null) {
      tabLabelAtMouse!!.revalidate()
      tabLabelAtMouse!!.repaint()
    }
  }

  fun unHover(label: TabLabel) {
    if (tabLabelAtMouse === label) {
      tabLabelAtMouse = null
      label.revalidate()
      label.repaint()
    }
  }

  fun isHoveredTab(label: TabLabel?): Boolean = label != null && label === tabLabelAtMouse

  open fun isActiveTabs(info: TabInfo?): Boolean = UIUtil.isFocusAncestor(this)

  override val isEditorTabs: Boolean
    get() = false

  fun supportsCompression(): Boolean = supportsCompression

  override fun setNavigationActionsEnabled(enabled: Boolean): JBTabs {
    navigationActionsEnabled = enabled
    return this
  }

  fun addNestedTabs(tabs: JBTabsImpl, parentDisposable: Disposable) {
    nestedTabs.add(tabs)
    Disposer.register(parentDisposable) { nestedTabs.remove(tabs) }
  }

  fun isDragOut(label: TabLabel?, deltaX: Int, deltaY: Int): Boolean {
    return effectiveLayout!!.isDragOut(label!!, deltaX, deltaY)
  }

  fun ignoreTabLabelLimitedWidthWhenPaint(): Boolean {
    return effectiveLayout is ScrollableSingleRowLayout || effectiveLayout is TableLayout && TabLayout.showPinnedTabsSeparately()
  }

  @RequiresEdt
  fun resetTabsCache() {
    allTabs = null
  }

  private fun processFocusChange() {
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    if (owner == null) {
      setFocused(false)
      return
    }

    if (owner === this || SwingUtilities.isDescendingFrom(owner, this)) {
      setFocused(focused = true)
    }
    else {
      setFocused(focused = false)
    }
  }

  private fun repaintAttractions() {
    var needsUpdate = false
    for (each in myVisibleInfos) {
      val eachLabel = infoToLabel.get(each)
      needsUpdate = needsUpdate or eachLabel!!.repaintAttraction()
    }
    if (needsUpdate) {
      relayout(forced = true, layoutNow = false)
    }
  }

  override fun addNotify() {
    super.addNotify()
    addTimerUpdate()
    scrollBarModel.addChangeListener(myScrollBarChangeListener)
    if (deferredFocusRequest != null) {
      val request = deferredFocusRequest!!
      deferredFocusRequest = null
      request.run()
    }
  }

  override fun remove(index: Int) {
    if (removeNotifyInProgress) {
      LOG.warn(IllegalStateException("removeNotify in progress"))
    }
    super.remove(index)
  }

  override fun removeAll() {
    if (removeNotifyInProgress) {
      LOG.warn(IllegalStateException("removeNotify in progress"))
    }
    super.removeAll()
  }

  override fun removeNotify() {
    try {
      removeNotifyInProgress = true
      super.removeNotify()
    }
    finally {
      removeNotifyInProgress = false
    }
    setFocused(false)
    removeTimerUpdate()
    scrollBarModel.removeChangeListener(myScrollBarChangeListener)
    if (ScreenUtil.isStandardAddRemoveNotify(this) && glassPane != null) {
      Disposer.dispose(tabActionsAutoHideListenerDisposable)
      tabActionsAutoHideListenerDisposable = Disposer.newDisposable()
      glassPane = null
    }
  }

  public override fun processMouseEvent(e: MouseEvent) {
    super.processMouseEvent(e)
  }

  private fun addTimerUpdate() {
    if (!listenerAdded) {
      ActionManager.getInstance().addTimerListener(this)
      listenerAdded = true
    }
  }

  private fun removeTimerUpdate() {
    if (listenerAdded) {
      ActionManager.getInstance().removeTimerListener(this)
      listenerAdded = false
    }
  }

  fun layoutComp(data: SingleRowPassInfo, deltaX: Int, deltaY: Int, deltaWidth: Int, deltaHeight: Int) {
    val hToolbar = data.hToolbar.get()
    val vToolbar = data.vToolbar.get()
    if (hToolbar != null) {
      val toolbarHeight = hToolbar.preferredSize.height
      val compRect = layoutComp(componentX = deltaX,
                                componentY = toolbarHeight + deltaY,
                                component = data.component.get()!!,
                                deltaWidth = deltaWidth,
                                deltaHeight = deltaHeight)
      layout(component = hToolbar,
             x = compRect.x,
             y = compRect.y - toolbarHeight,
             width = compRect.width,
             height = toolbarHeight)
    }
    else if (vToolbar != null) {
      val toolbarWidth = vToolbar.preferredSize.width
      val vSeparatorWidth = if (toolbarWidth > 0) 1 else 0
      if (isSideComponentBefore) {
        val compRect = layoutComp(componentX = toolbarWidth + vSeparatorWidth + deltaX,
                                  componentY = deltaY,
                                  component = data.component.get()!!,
                                  deltaWidth = deltaWidth,
                                  deltaHeight = deltaHeight)
        layout(component = vToolbar,
               x = compRect.x - toolbarWidth - vSeparatorWidth,
               y = compRect.y,
               width = toolbarWidth,
               height = compRect.height)
      }
      else {
        val compRect = layoutComp(bounds = Rectangle(deltaX, deltaY, width - toolbarWidth - vSeparatorWidth, height),
                                  component = data.component.get()!!,
                                  deltaWidth = deltaWidth,
                                  deltaHeight = deltaHeight)
        layout(component = vToolbar,
               x = compRect.x + compRect.width + vSeparatorWidth,
               y = compRect.y,
               width = toolbarWidth,
               height = compRect.height)
      }
    }
    else {
      layoutComp(componentX = deltaX,
                 componentY = deltaY,
                 component = data.component.get()!!,
                 deltaWidth = deltaWidth,
                 deltaHeight = deltaHeight)
    }
  }

  fun isDropTarget(info: TabInfo): Boolean = dropInfo != null && dropInfo == info

  fun getFirstTabOffset(): Int = firstTabOffset

  override fun setFirstTabOffset(firstTabOffset: Int) {
    this.firstTabOffset = firstTabOffset
  }

  override fun setEmptyText(text: String?): JBTabsPresentation {
    emptyText = text
    return this
  }

  /**
   * TODO use RdGraphicsExKt#childAtMouse(IdeGlassPane, Container)
   */
  @Deprecated("")
  internal inner class TabActionsAutoHideListener : MouseMotionAdapter(), Weighted {
    private var currentOverLabel: TabLabel? = null
    private var lastOverPoint: Point? = null
    override fun getWeight(): Double = 1.0

    override fun mouseMoved(e: MouseEvent) {
      if (!myTabLabelActionsAutoHide) return
      lastOverPoint = SwingUtilities.convertPoint(e.component, e.x, e.y, this@JBTabsImpl)
      processMouseOver()
    }

    fun processMouseOver() {
      if (!myTabLabelActionsAutoHide || lastOverPoint == null) {
        return
      }

      if (lastOverPoint!!.x in 0 until width && lastOverPoint!!.y > 0 && lastOverPoint!!.y < height) {
        val label = infoToLabel[_findInfo(lastOverPoint!!, true)]
        if (label != null) {
          if (currentOverLabel != null) {
            currentOverLabel!!.toggleShowActions(false)
          }
          label.toggleShowActions(true)
          currentOverLabel = label
          return
        }
      }
      if (currentOverLabel != null) {
        currentOverLabel!!.toggleShowActions(false)
        currentOverLabel = null
      }
    }
  }

  override fun getModalityState(): ModalityState = ModalityState.stateForComponent(this)

  override fun run() {
    updateTabActions(false)
  }

  override fun updateTabActions(validateNow: Boolean) {
    if (isHideTabs) return
    var changed = false
    for (tabLabel in infoToLabel.values) {
      val changes = tabLabel.updateTabActions()
      changed = changed || changes
    }
    if (changed) {
      revalidateAndRepaint()
    }
  }

  val entryPointPreferredSize: Dimension
    get() = if (entryPointToolbar == null) Dimension() else entryPointToolbar!!.component.preferredSize

  val moreToolbarPreferredSize: Dimension
    // Returns default one action horizontal toolbar size (26x24)
    get() {
      val baseSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      return Dimension(baseSize.width + JBUI.scale(4), baseSize.height + JBUI.scale(2))
    }

  private val entryPointRect: Rectangle?
    get() {
      if (effectiveLayout is SingleRowLayout) {
        val lastLayout = singleRowLayout.myLastSingRowLayout
        return lastLayout?.entryPointRect
      }
      if (effectiveLayout is TableLayout) {
        val lastLayout = tableLayout.myLastTableLayout
        return lastLayout?.entryPointRect
      }
      return null
    }

  private val moreRect: Rectangle?
    get() {
      if (effectiveLayout is SingleRowLayout) {
        val lastLayout = singleRowLayout.myLastSingRowLayout
        return lastLayout?.moreRect
      }
      if (effectiveLayout is TableLayout) {
        val lastLayout = tableLayout.myLastTableLayout
        return lastLayout?.moreRect
      }
      return null
    }

  private val titleRect: Rectangle?
    get() {
      if (effectiveLayout is SingleRowLayout) {
        val lastLayout = singleRowLayout.myLastSingRowLayout
        return lastLayout?.titleRect
      }
      if (effectiveLayout is TableLayout) {
        val lastLayout = tableLayout.myLastTableLayout
        return lastLayout?.titleRect
      }
      return null
    }

  override fun setTitleProducer(titleProducer: (() -> Pair<Icon, @Nls String>)?) {
    titleWrapper.removeAll()
    if (titleProducer != null) {
      val toolbar = ActionManager.getInstance().createActionToolbar(/* place = */ ActionPlaces.TABS_MORE_TOOLBAR,
                                                                    /* group = */DefaultActionGroup(
        TitleAction(tabs = this, titleProvider = titleProducer)),
                                                                    /* horizontal = */ true)
      toolbar.targetComponent = null
      toolbar.setMiniMode(true)
      titleWrapper.setContent(toolbar.component)
    }
  }

  override fun canShowMorePopup(): Boolean {
    val rect = moreRect
    return rect != null && !rect.isEmpty
  }

  override fun showMorePopup(): JBPopup? {
    val rect = moreRect ?: return null
    val hiddenInfos = visibleInfos.filter(singleRowLayout::isTabHidden)
    if (ExperimentalUI.isNewUI()) {
      return showListPopup(rect = rect, hiddenInfos = hiddenInfos)
    }
    else {
      return showTabLabelsPopup(rect = rect, hiddenInfos = hiddenInfos)
    }
  }

  private fun showListPopup(rect: Rectangle, hiddenInfos: List<TabInfo>): JBPopup {
    val separatorIndex = hiddenInfos.indexOfFirst { info ->
      val label = infoToLabel.get(info)
      if (position.isSide) {
        label!!.y >= 0
      }
      else {
        label!!.x >= 0
      }
    }

    val separatorInfo = if (separatorIndex > 0) hiddenInfos[separatorIndex] else null
    val step = HiddenInfosListPopupStep(hiddenInfos, separatorInfo)
    val selectedIndex = ClientProperty.get(this, HIDDEN_INFOS_SELECT_INDEX_KEY)
    if (selectedIndex != null) {
      step.defaultOptionIndex = selectedIndex
    }
    val popup = JBPopupFactory.getInstance().createListPopup(myProject!!, step) {
      val descriptor = object : ListItemDescriptorAdapter<TabInfo>() {
        @Suppress("DialogTitleCapitalization")
        override fun getTextFor(value: TabInfo): String = value.text

        override fun getIconFor(value: TabInfo): Icon? = value.icon

        override fun hasSeparatorAboveOf(value: TabInfo): Boolean = value == separatorInfo
      }
      object : GroupedItemsListRenderer<TabInfo?>(descriptor) {
        private val HOVER_INDEX_KEY = Key.create<Int>("HOVER_INDEX")
        private val TAB_INFO_KEY = Key.create<TabInfo?>("TAB_INFO")
        private val SELECTED_KEY = Key.create<Boolean>("SELECTED")
        var component: JPanel? = null
        var iconLabel: JLabel? = null
        var textLabel: SimpleColoredComponent? = null
        var actionLabel: JLabel? = null
        var listMouseListener: MouseAdapter? = null
        override fun createItemComponent(): JComponent {
          // there is the separate label 'textLabel', but the original one still should be created,
          // as it is used from the GroupedElementsRenderer.configureComponent
          createLabel()
          component = JPanel()
          val layout = BoxLayout(component, BoxLayout.X_AXIS)
          component!!.layout = layout
          // painting underline for the selected tab
          component!!.border = object : Border {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
              if (!ClientProperty.isTrue(c, SELECTED_KEY)) {
                return
              }
              val inset = JBUI.scale(2)
              val arc = JBUI.scale(4)
              val theme: TabTheme = tabPainter.getTabTheme()

              @Suppress("NAME_SHADOWING")
              val rect = Rectangle(x, y + inset, theme.underlineHeight, height - inset * 2)
              (g as Graphics2D).fill2DRoundRect(rect, arc.toDouble(), theme.underlineColor)
            }

            override fun getBorderInsets(c: Component): Insets {
              return JBInsets.create(Insets(0, 9, 0, 3))
            }

            override fun isBorderOpaque(): Boolean {
              return true
            }
          }
          val settings = getInstance()
          if (!settings.closeTabButtonOnTheRight) {
            addActionLabel()
            val gap = JBUI.CurrentTheme.ActionsList.elementIconGap() - 2
            component!!.add(Box.createRigidArea(Dimension(gap, 0)))
          }
          iconLabel = JLabel()
          component!!.add(iconLabel)
          val gap = JBUI.CurrentTheme.ActionsList.elementIconGap() - 2
          component!!.add(Box.createRigidArea(Dimension(gap, 0)))
          textLabel = object : SimpleColoredComponent() {
            override fun getMaximumSize(): Dimension {
              return preferredSize
            }
          }
          textLabel!!.setMyBorder(null)
          textLabel!!.setIpad(JBInsets.emptyInsets())
          textLabel!!.setOpaque(true)
          component!!.add(textLabel)
          if (settings.closeTabButtonOnTheRight) {
            component!!.add(Box.createRigidArea(JBDimension(30, 0)))
            component!!.add(Box.createHorizontalGlue())
            addActionLabel()
          }
          val result = layoutComponent(component)
          if (result is SelectablePanel) {
            result.setBorder(JBUI.Borders.empty(0, 5))
            result.selectionInsets = JBInsets.create(0, 5)
            result.preferredHeight = JBUI.scale(26)
          }
          return result
        }

        private fun addActionLabel() {
          actionLabel = JLabel()
          component!!.add(actionLabel)
        }

        override fun customizeComponent(list: JList<out TabInfo?>?, info: TabInfo?, isSelected: Boolean) {
          if (actionLabel != null) {
            val isHovered = ClientProperty.get(list, HOVER_INDEX_KEY) == myCurrentIndex
            val icon = getTabActionIcon(info!!, isHovered)
            actionLabel!!.icon = icon
            ClientProperty.put(actionLabel!!, TAB_INFO_KEY, info)
            addMouseListener(list!!)
          }
          val selectedInfo = selectedInfo
          var icon = info?.icon
          if (icon != null && info != selectedInfo) {
            icon = getTransparentIcon(icon, JBUI.CurrentTheme.EditorTabs.unselectedAlpha())
          }
          iconLabel!!.icon = icon
          textLabel!!.clear()
          info!!.coloredText.appendToComponent(textLabel!!)
          val customBackground = info.tabColor
          myRendererComponent.background = customBackground ?: JBUI.CurrentTheme.Popup.BACKGROUND
          ClientProperty.put(component!!, SELECTED_KEY, if (info == selectedInfo) true else null)
          component!!.invalidate()
        }

        override fun setComponentIcon(icon: Icon, disabledIcon: Icon) {
          // the icon will be set in customizeComponent
        }

        override fun createSeparator(): SeparatorWithText {
          val labelInsets = JBUI.CurrentTheme.Popup.separatorLabelInsets()
          return GroupHeaderSeparator(labelInsets)
        }

        private fun addMouseListener(list: JList<out TabInfo>) {
          if (listMouseListener != null) {
            return
          }

          listMouseListener = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
              val point = e.locationOnScreen
              SwingUtilities.convertPointFromScreen(point, list)
              val hoveredIndex = list.locationToIndex(point)
              val renderer = ListUtil.getDeepestRendererChildComponentAt(list, e.point)
              updateHoveredIconIndex(if (ClientProperty.get(renderer, TAB_INFO_KEY) != null) hoveredIndex else -1)
            }

            override fun mouseExited(e: MouseEvent) {
              updateHoveredIconIndex(-1)
            }

            private fun updateHoveredIconIndex(hoveredIndex: Int) {
              val oldIndex = ClientProperty.get(list, HOVER_INDEX_KEY)
              ClientProperty.put(list, HOVER_INDEX_KEY, hoveredIndex)
              if (oldIndex != hoveredIndex) {
                list.repaint()
              }
            }

            override fun mouseReleased(e: MouseEvent) {
              val point = e.locationOnScreen
              SwingUtilities.convertPointFromScreen(point, list)
              val clickedIndex = list.locationToIndex(point)
              val renderer = ListUtil.getDeepestRendererChildComponentAt(list, e.point)
              if (renderer !is JLabel) {
                return
              }
              val tabInfo = ClientProperty.get(renderer, TAB_INFO_KEY) ?: return

              // The last one is expected to be 'CloseTab'
              val tabAction = if (tabInfo.tabLabelActions != null) tabInfo.getTabLabelActions().getChildren(null).lastOrNull() else null
              if (tabAction == null && !tabInfo.isPinned) {
                return
              }

              var clickToUnpin = false
              if (tabInfo.isPinned()) {
                if (tabAction != null) {
                  val component = tabInfo.getComponent()
                  val wasShowing = UIUtil.isShowing(component)
                  try {
                    UIUtil.markAsShowing(component, true)
                    ActionManager.getInstance().tryToExecute(tabAction, e, tabInfo.getComponent(), tabInfo.getTabActionPlace(), true)
                  }
                  finally {
                    UIUtil.markAsShowing(component, wasShowing)
                  }
                  clickToUnpin = true
                }
              }
              if (!clickToUnpin) {
                removeTab(tabInfo)
              }
              e.consume()
              val indexToSelect = Math.min(clickedIndex, list.model.size)
              ClientProperty.put(this@JBTabsImpl, HIDDEN_INFOS_SELECT_INDEX_KEY, indexToSelect)
              step.selectTab = false // do not select current tab, because we already handled other action: close or unpin
              val curPopup = PopupUtil.getPopupContainerFor(list)
              if (curPopup != null) {
                val button = PopupUtil.getPopupToggleComponent(curPopup)
                curPopup.cancel()
                if (list.model.size > 0) {
                  val newPopup = showMorePopup()
                  if (newPopup != null) {
                    PopupUtil.setPopupToggleComponent(newPopup, button)
                  }
                }
              }
            }
          }
          val listeners = list.mouseListeners
          val motionListeners = list.mouseMotionListeners
          listeners.forEach(list::removeMouseListener)
          motionListeners.forEach(list::removeMouseMotionListener)
          list.addMouseListener(listMouseListener)
          list.addMouseMotionListener(listMouseListener)
          listeners.forEach(list::addMouseListener)
          motionListeners.forEach(list::addMouseMotionListener)
        }
      }
    }
    popup.content.putClientProperty(MorePopupAware::class.java, true)
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        ApplicationManager.getApplication().invokeLater { ClientProperty.put(this@JBTabsImpl, HIDDEN_INFOS_SELECT_INDEX_KEY, null) }
      }
    })
    popup.show(RelativePoint(this, Point(rect.x, rect.y + rect.height)))
    return popup
  }

  // returns the icon that will be used in the hidden tabs list
  protected open fun getTabActionIcon(info: TabInfo, isHovered: Boolean): Icon? {
    val hasActions = info.tabLabelActions != null && info.tabLabelActions.getChildren(null).size > 0
    var icon: Icon?
    icon = if (hasActions) {
      if (isHovered) AllIcons.Actions.CloseHovered else AllIcons.Actions.Close
    }
    else {
      EmptyIcon.ICON_16
    }
    if (info.isPinned) {
      icon = AllIcons.Actions.PinTab
    }
    return icon
  }

  private inner class HiddenInfosListPopupStep(values: List<TabInfo>, private val separatorInfo: TabInfo?) : BaseListPopupStep<TabInfo>(
    null, values) {
    var selectTab = true
    override fun onChosen(selectedValue: TabInfo, finalChoice: Boolean): PopupStep<*>? {
      if (selectTab) {
        select(selectedValue, true)
      }
      else {
        selectTab = true
      }
      return FINAL_CHOICE
    }

    override fun getSeparatorAbove(value: TabInfo): ListSeparator? = if (value == separatorInfo) ListSeparator() else null

    override fun getIconFor(value: TabInfo): Icon? = value.icon

    override fun getTextFor(value: TabInfo): String {
      @Suppress("DialogTitleCapitalization")
      return value.text
    }
  }

  private fun showTabLabelsPopup(rect: Rectangle, hiddenInfos: List<TabInfo>): JBPopup {
    val gridPanel = JPanel(GridLayout(hiddenInfos.size, 1))
    val scrollPane: JScrollPane = object : JBScrollPane(gridPanel) {
      override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        if (ScreenUtil.getScreenRectangle(this@JBTabsImpl).height < gridPanel.preferredSize.height) {
          size.width += UIUtil.getScrollBarWidth()
        }
        return size
      }
    }
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, null).createPopup()
    for (info in hiddenInfos) {
      val label = createTabLabel(info)
      label.isDoubleBuffered = true
      label.setText(info.coloredText)
      label.setIcon(info.icon)
      label.setTabActions(info.tabLabelActions)
      label.setAlignmentToCenter(false)
      label.apply(if (uiDecorator != null) uiDecorator!!.decoration else defaultDecorator.decoration)
      label.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.isShiftDown && !e.isPopupTrigger) {
            removeTab(info)
            if (canShowMorePopup()) {
              showMorePopup()
            }
            popup.cancel()
          }
          else {
            select(info, true)
          }
        }
      })
      add(label)
      try {
        label.updateTabActions()
      }
      finally {
        remove(label)
      }
      gridPanel.add(label)
    }
    popup.content.putClientProperty(MorePopupAware::class.java, true)
    popup.show(RelativePoint(this, Point(rect.x, rect.y + rect.height)))
    return popup
  }

  private val toFocus: JComponent?
    get() {
      val info = selectedInfo
      LOG.debug {
        "selected info: $info"
      }
      if (info == null) return null
      var toFocus: JComponent? = null
      if (isRequestFocusOnLastFocusedComponent && info.lastFocusOwner != null && !isMyChildIsFocusedNow) {
        toFocus = info.lastFocusOwner
        if (LOG.isDebugEnabled) {
          LOG.debug("last focus owner: $toFocus")
        }
      }
      if (toFocus == null) {
        toFocus = info.preferredFocusableComponent
        if (LOG.isDebugEnabled) {
          LOG.debug("preferred focusable component: $toFocus")
        }
        if (toFocus == null || !toFocus.isShowing) {
          return null
        }
        val policyToFocus = focusManager.getFocusTargetFor(toFocus)
        if (LOG.isDebugEnabled) {
          LOG.debug("focus target: $policyToFocus")
        }
        if (policyToFocus != null) {
          toFocus = policyToFocus
        }
      }
      return toFocus
    }

  override fun requestFocus() {
    val toFocus = toFocus
    if (toFocus != null) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(toFocus, true) }
    }
    else {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { super.requestFocus() }
    }
  }

  override fun requestFocusInWindow(): Boolean {
    return this.toFocus?.requestFocusInWindow() ?: super.requestFocusInWindow()
  }

  override fun addTab(info: TabInfo, index: Int): TabInfo {
    return addTab(info = info, index = index, isDropTarget = false, fireEvents = true)
  }

  override fun addTabSilently(info: TabInfo, index: Int): TabInfo {
    return addTab(info = info, index = index, isDropTarget = false, fireEvents = false)
  }

  private fun addTab(info: TabInfo, index: Int, isDropTarget: Boolean, fireEvents: Boolean): TabInfo {
    if (!isDropTarget && tabs.contains(info)) {
      return tabs.get(tabs.indexOf(info))
    }
    info.changeSupport.addPropertyChangeListener(this)
    val label = createTabLabel(info)
    infoToLabel.put(info, label)
    infoToPage.put(info, AccessibleTabPage(parent = this, tabInfo = info))
    if (!isDropTarget) {
      if (index < 0 || index > myVisibleInfos.size - 1) {
        myVisibleInfos.add(info)
      }
      else {
        myVisibleInfos.add(index, info)
      }
    }
    resetTabsCache()
    updateText(info)
    updateIcon(info)
    updateSideComponent(info)
    updateTabActions(info)
    add(label)
    adjust(info)
    updateAll(false)
    if (info.isHidden) {
      updateHiding()
    }
    if (!isDropTarget && fireEvents) {
      if (tabCount == 1) {
        fireBeforeSelectionChanged(null, info)
        fireSelectionChanged(null, info)
      }
    }
    revalidateAndRepaint(false)
    return info
  }

  protected open fun createTabLabel(info: TabInfo): TabLabel = TabLabel(this, info)

  override fun addTab(info: TabInfo): TabInfo = addTab(info, -1)

  override fun getTabLabel(info: TabInfo): TabLabel = infoToLabel.get(info)!!

  val popupGroup: ActionGroup?
    get() = if (myPopupGroup != null) myPopupGroup!!.get() else null

  override fun setPopupGroup(popupGroup: ActionGroup, place: String, addNavigationGroup: Boolean): JBTabs {
    return setPopupGroup({ popupGroup }, place, addNavigationGroup)
  }

  override fun setPopupGroup(popupGroup: Supplier<out ActionGroup>,
                             place: String,
                             addNavigationGroup: Boolean): JBTabs {
    myPopupGroup = popupGroup
    popupPlace = place
    this.addNavigationGroup = addNavigationGroup
    return this
  }

  private fun updateAll(forcedRelayout: Boolean) {
    val toSelect = selectedInfo
    setSelectedInfo(toSelect)
    updateContainer(forcedRelayout, false)
    removeDeferred()
    updateListeners()
    updateTabActions(false)
    updateEnabling()
  }

  private val isMyChildIsFocusedNow: Boolean
    get() {
      val owner = getFocusOwner() ?: return false
      if (mySelectedInfo != null && !SwingUtilities.isDescendingFrom(owner, mySelectedInfo!!.component)) {
        return false
      }
      else {
        return SwingUtilities.isDescendingFrom(owner, this)
      }
    }

  override fun select(info: TabInfo, requestFocus: Boolean): ActionCallback {
    return _setSelected(info = info, requestFocus = requestFocus, requestFocusInWindow = false)
  }

  private fun _setSelected(info: TabInfo, requestFocus: Boolean, requestFocusInWindow: Boolean): ActionCallback {
    if (!isEnabled) {
      return ActionCallback.REJECTED
    }
    isMouseInsideTabsArea = false //temporary state to make selection fully visible (scrolled in view)
    return if (mySelectionChangeHandler != null) {
      mySelectionChangeHandler!!.execute(info, requestFocus, object : ActiveRunnable() {
        override fun run(): ActionCallback {
          return executeSelectionChange(info, requestFocus, requestFocusInWindow)
        }
      })
    }
    else executeSelectionChange(info, requestFocus, requestFocusInWindow)
  }

  private fun executeSelectionChange(info: TabInfo, requestFocus: Boolean, requestFocusInWindow: Boolean): ActionCallback {
    if (mySelectedInfo != null && mySelectedInfo == info) {
      if (!requestFocus) {
        return ActionCallback.DONE
      }
      val owner = focusManager.focusOwner
      val c = info.component
      return if (c != null && owner != null && (c === owner || SwingUtilities.isDescendingFrom(owner, c))) {
        // This might look like a no-op, but in some cases it's not. In particular, it's required when a focus transfer has just been
        // requested to another component. E.g. this happens on 'unsplit' operation when we remove an editor component from UI hierarchy and
        // re-add it at once in a different layout, and want that editor component to preserve focus afterwards.
        requestFocus(owner, requestFocusInWindow)
      }
      else requestFocus(toFocus, requestFocusInWindow)
    }
    if (isRequestFocusOnLastFocusedComponent && mySelectedInfo != null && isMyChildIsFocusedNow) {
      mySelectedInfo!!.lastFocusOwner = focusOwnerToStore
    }
    val oldInfo = mySelectedInfo
    setSelectedInfo(info)
    val newInfo = selectedInfo
    val label = infoToLabel[info]
    if (label != null) {
      setComponentZOrder(label, 0)
    }
    setComponentZOrder(scrollBar, 0)
    fireBeforeSelectionChanged(oldInfo, newInfo)
    val oldValue = isMouseInsideTabsArea
    try {
      updateContainer(false, true)
    }
    finally {
      isMouseInsideTabsArea = oldValue
    }
    fireSelectionChanged(oldInfo, newInfo)
    if (!requestFocus) {
      return removeDeferred()
    }
    val toFocus = toFocus
    return if (myProject != null && toFocus != null) {
      val result = ActionCallback()
      requestFocus(toFocus, requestFocusInWindow).doWhenProcessed {
        if (myProject!!.isDisposed) {
          result.setRejected()
        }
        else {
          removeDeferred().notifyWhenDone(result)
        }
      }
      result
    }
    else {
      ApplicationManager.getApplication().invokeLater({
                                                        if (requestFocusInWindow) {
                                                          requestFocusInWindow()
                                                        }
                                                        else {
                                                          focusManager.requestFocusInProject(this,
                                                                                             myProject)
                                                        }
                                                      }, ModalityState.NON_MODAL)
      removeDeferred()
    }
  }

  protected open val focusOwnerToStore: JComponent?
    get() {
      val owner = getFocusOwner() ?: return null
      val tabs = ComponentUtil.getParentOfType(JBTabsImpl::class.java, owner.parent)
      return if (tabs !== this) null else owner
    }

  private fun fireBeforeSelectionChanged(oldInfo: TabInfo?, newInfo: TabInfo?) {
    if (oldInfo != newInfo) {
      myOldSelection = oldInfo
      try {
        for (eachListener in myTabListeners) {
          eachListener.beforeSelectionChanged(oldInfo, newInfo)
        }
      }
      finally {
        myOldSelection = null
      }
    }
  }

  private fun fireSelectionChanged(oldInfo: TabInfo?, newInfo: TabInfo?) {
    if (oldInfo != newInfo) {
      for (eachListener in myTabListeners) {
        eachListener?.selectionChanged(oldInfo, newInfo)
      }
    }
  }

  fun fireTabsMoved() {
    for (eachListener in myTabListeners) {
      eachListener?.tabsMoved()
    }
  }

  private fun fireTabRemoved(info: TabInfo) {
    for (eachListener in myTabListeners) {
      eachListener?.tabRemoved(info)
    }
  }

  private fun requestFocus(toFocus: Component?, inWindow: Boolean): ActionCallback {
    if (toFocus == null) return ActionCallback.DONE
    if (isShowing) {
      val res = ActionCallback()
      ApplicationManager.getApplication().invokeLater {
        if (inWindow) {
          toFocus.requestFocusInWindow()
          res.setDone()
        }
        else {
          focusManager.requestFocusInProject(toFocus, myProject).notifyWhenDone(res)
        }
      }
      return res
    }
    return ActionCallback.REJECTED
  }

  private fun removeDeferred(): ActionCallback {
    if (myDeferredToRemove.isEmpty()) {
      return ActionCallback.DONE
    }
    val callback = ActionCallback()
    val executionRequest = ++myRemoveDeferredRequest
    focusManager.doWhenFocusSettlesDown {
      if (myRemoveDeferredRequest == executionRequest) {
        removeDeferredNow()
      }
      callback.setDone()
    }
    return callback
  }

  private fun unqueueFromRemove(c: Component) {
    myDeferredToRemove.remove(c)
  }

  private fun removeDeferredNow() {
    for (each in myDeferredToRemove.keys) {
      if (each != null && each.parent === this) {
        remove(each)
      }
    }
    myDeferredToRemove.clear()
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    val tabInfo = evt.source as TabInfo
    if (TabInfo.ACTION_GROUP == evt.propertyName) {
      updateSideComponent(tabInfo)
      relayout(false, false)
    }
    else if (TabInfo.COMPONENT == evt.propertyName) {
      relayout(true, false)
    }
    else if (TabInfo.TEXT == evt.propertyName) {
      updateText(tabInfo)
      revalidateAndRepaint()
    }
    else if (TabInfo.ICON == evt.propertyName) {
      updateIcon(tabInfo)
      revalidateAndRepaint()
    }
    else if (TabInfo.TAB_COLOR == evt.propertyName) {
      revalidateAndRepaint()
    }
    else if (TabInfo.ALERT_STATUS == evt.propertyName) {
      val start = evt.newValue as Boolean
      updateAttraction(tabInfo, start)
    }
    else if (TabInfo.TAB_ACTION_GROUP == evt.propertyName) {
      updateTabActions(tabInfo)
      relayout(false, false)
    }
    else if (TabInfo.HIDDEN == evt.propertyName) {
      updateHiding()
      relayout(false, false)
    }
    else if (TabInfo.ENABLED == evt.propertyName) {
      updateEnabling()
    }
  }

  private fun updateEnabling() {
    val all = tabs
    for (tabInfo in all) {
      val eachLabel = infoToLabel[tabInfo]
      eachLabel!!.setTabEnabled(tabInfo.isEnabled)
    }
    val selected = selectedInfo
    if (selected != null && !selected.isEnabled) {
      val toSelect = getToSelectOnRemoveOf(selected)
      if (toSelect != null) {
        select(info = toSelect, requestFocus = focusManager.getFocusedDescendantFor(this) != null)
      }
    }
  }

  private fun updateHiding() {
    var update = false
    val visible = myVisibleInfos.iterator()
    while (visible.hasNext()) {
      val tabInfo = visible.next()
      if (tabInfo.isHidden && !myHiddenInfos.containsKey(tabInfo)) {
        myHiddenInfos.put(tabInfo, myVisibleInfos.indexOf(tabInfo))
        visible.remove()
        update = true
      }
    }
    val hidden = myHiddenInfos.keys.iterator()
    while (hidden.hasNext()) {
      val each = hidden.next()
      if (!each.isHidden && myHiddenInfos.containsKey(each)) {
        myVisibleInfos.add(getIndexInVisibleArray(each), each)
        hidden.remove()
        update = true
      }
    }
    if (update) {
      resetTabsCache()
      if (mySelectedInfo != null && myHiddenInfos.containsKey(mySelectedInfo)) {
        val toSelect = getToSelectOnRemoveOf(mySelectedInfo!!)
        setSelectedInfo(toSelect)
      }
      updateAll(true)
    }
  }

  private fun getIndexInVisibleArray(each: TabInfo): Int {
    val info = myHiddenInfos[each]
    var index = info ?: myVisibleInfos.size
    if (index > myVisibleInfos.size) {
      index = myVisibleInfos.size
    }
    if (index < 0) {
      index = 0
    }
    return index
  }

  private fun updateIcon(tabInfo: TabInfo) {
    infoToLabel.get(tabInfo)!!.setIcon(tabInfo.icon)
  }

  fun revalidateAndRepaint() {
    revalidateAndRepaint(true)
  }

  override fun isOpaque(): Boolean {
    return super.isOpaque() && !myVisibleInfos.isEmpty()
  }

  open fun revalidateAndRepaint(layoutNow: Boolean) {
    if (myVisibleInfos.isEmpty() && parent != null) {
      val nonOpaque = ComponentUtil.findUltimateParent(this)
      val toRepaint = SwingUtilities.convertRectangle(parent, bounds, nonOpaque)
      nonOpaque.repaint(toRepaint.x, toRepaint.y, toRepaint.width, toRepaint.height)
    }
    if (layoutNow) {
      validate()
    }
    else {
      revalidate()
    }
    repaint()
  }

  private fun updateAttraction(tabInfo: TabInfo, start: Boolean) {
    if (start) {
      attractions.add(tabInfo)
    }
    else {
      attractions.remove(tabInfo)
      tabInfo.blinkCount = 0
    }
    if (start && !animator.isRunning) {
      animator.resume()
    }
    else if (!start && attractions.isEmpty()) {
      animator.suspend()
      repaintAttractions()
    }
  }

  private fun updateText(tabInfo: TabInfo) {
    val label = infoToLabel[tabInfo]
    label!!.setText(tabInfo.coloredText)
    label.toolTipText = tabInfo.tooltipText
  }

  private fun updateSideComponent(tabInfo: TabInfo) {
    val old = infoToToolbar.get(tabInfo)
    old?.let { remove(it) }
    val toolbar = createToolbarComponent(tabInfo)
    infoToToolbar.put(tabInfo, toolbar)
    add(toolbar)
  }

  private fun updateTabActions(info: TabInfo) {
    infoToLabel.get(info)!!.setTabActions(info.tabLabelActions)
  }

  override fun getSelectedInfo(): TabInfo? {
    if (myOldSelection != null) {
      return myOldSelection
    }

    if (mySelectedInfo == null) {
      return if (myVisibleInfos.isEmpty()) null else myVisibleInfos[0]
    }
    else if (myVisibleInfos.contains(mySelectedInfo)) {
      return mySelectedInfo
    }
    else {
      setSelectedInfo(null)
      return null
    }
  }

  private fun setSelectedInfo(info: TabInfo?) {
    mySelectedInfo = info
    infoToToolbar.forEach { (tabInfo: TabInfo?, toolbar: Toolbar) -> toolbar.isVisible = info == tabInfo }
  }

  override fun getToSelectOnRemoveOf(info: TabInfo): TabInfo? {
    if (!myVisibleInfos.contains(info) || mySelectedInfo != info || myVisibleInfos.size == 1) {
      return null
    }

    val index = visibleInfos.indexOf(info)
    var result: TabInfo? = null
    if (index > 0) {
      result = findEnabledBackward(index, false)
    }
    if (result == null) {
      result = findEnabledForward(index, false)
    }
    return result
  }

  fun findEnabledForward(from: Int, cycle: Boolean): TabInfo? {
    if (from < 0) {
      return null
    }
    var index = from
    val infos = visibleInfos
    while (true) {
      index++
      if (index == infos.size) {
        if (!cycle) {
          break
        }
        index = 0
      }
      if (index == from) {
        break
      }
      val tabInfo = infos.get(index)
      if (tabInfo.isEnabled) {
        return tabInfo
      }
    }
    return null
  }

  fun findEnabledBackward(from: Int, cycle: Boolean): TabInfo? {
    if (from < 0) {
      return null
    }
    var index = from
    val infos = visibleInfos
    while (true) {
      index--
      if (index == -1) {
        if (!cycle) {
          break
        }
        index = infos.size - 1
      }
      if (index == from) {
        break
      }
      val each = infos[index]
      if (each.isEnabled) {
        return each
      }
    }
    return null
  }

  private fun createToolbarComponent(tabInfo: TabInfo): Toolbar = Toolbar(tabs = this, info = tabInfo)

  override fun getTabAt(tabIndex: Int): TabInfo = tabs.get(tabIndex)

  override fun getTabs(): List<TabInfo> {
    EDT.assertIsEdt()
    if (allTabs != null) {
      return allTabs!!
    }
    val result = ArrayList(myVisibleInfos)
    for (each in myHiddenInfos.keys) {
      result.add(getIndexInVisibleArray(each), each)
    }
    if (isAlphabeticalMode()) {
      sortTabsAlphabetically(result)
    }
    allTabs = result
    return result
  }

  override fun getTargetInfo(): TabInfo? {
    return if (popupInfo != null) popupInfo else selectedInfo
  }

  override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}
  override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
    resetPopup()
  }

  override fun popupMenuCanceled(e: PopupMenuEvent) {
    resetPopup()
  }

  private fun resetPopup() {
    //todo [kirillk] dirty hack, should rely on ActionManager to understand that menu item was either chosen on or cancelled
    SwingUtilities.invokeLater {
      // No need to reset popup info if a new popup has been already opened and myPopupInfo refers to the corresponding info.
      if (activePopup == null) {
        popupInfo = null
      }
    }
  }

  override fun setPaintBlocked(blocked: Boolean, takeSnapshot: Boolean) {}
  private fun addToDeferredRemove(c: Component) {
    if (!myDeferredToRemove.containsKey(c)) {
      myDeferredToRemove[c] = c
    }
  }

  override fun setToDrawBorderIfTabsHidden(toDrawBorderIfTabsHidden: Boolean): JBTabsPresentation {
    return this
  }

  override fun getJBTabs(): JBTabs {
    return this
  }

  class Toolbar(private val tabs: JBTabsImpl, private val info: TabInfo) : NonOpaquePanel() {
    init {
      layout = BorderLayout()
      val group = info.group
      val side = info.sideComponent
      if (group != null) {
        val place = info.place
        val toolbar = ActionManager.getInstance().createActionToolbar(
          if (place != null && place != ActionPlaces.UNKNOWN) place else "JBTabs", group, tabs.horizontalSide)
        toolbar.targetComponent = info.actionsContextComponent
        val actionToolbar = toolbar.component
        add(actionToolbar, BorderLayout.CENTER)
      }
      if (side != null) {
        if (group != null) {
          add(side, BorderLayout.EAST)
        }
        else {
          add(side, BorderLayout.CENTER)
        }
      }
      UIUtil.uiTraverser(this).filter { c: Component? ->
        !UIUtil.canDisplayFocusedState(
          c!!)
      }.forEach(
        Consumer { c: Component -> c.isFocusable = false })
    }

    override fun getPreferredSize(): Dimension {
      val base = super.getPreferredSize()
      val label = tabs.infoToLabel[info]
      return if (tabs.horizontalSide && tabs.isSideComponentOnTabs && label != null && base.height > 0) {
        Dimension(base.width, label.preferredSize.height - tabs.borderThickness)
      }
      else base
    }

    val isEmpty: Boolean
      get() = componentCount == 0
  }

  private fun updateScrollBarModel() {
    val scrollBarModel = scrollBarModel
    if (scrollBarModel.valueIsAdjusting) return
    val maximum = lastLayoutPass!!.requiredLength
    val value = effectiveLayout!!.scrollOffset
    var extent: Int
    if (isHorizontalTabs) {
      extent = tabsAreaWidth
    }
    else {
      extent = height
      if (!ExperimentalUI.isNewUI() && entryPointToolbar != null && entryPointToolbar!!.component.isVisible) {
        extent = entryPointToolbar!!.component.y
      }
    }
    scrollBarModel.maximum = maximum
    scrollBarModel.value = value
    // If extent is 0, that means the layout is in improper state, so we don't show the scrollbar.
    scrollBarModel.extent = if (extent == 0) value + maximum else extent
  }

  private fun updateTabsOffsetFromScrollBar() {
    val currentUnitsOffset = effectiveLayout!!.scrollOffset
    val updatedOffset = scrollBarModel.value
    effectiveLayout!!.scroll(updatedOffset - currentUnitsOffset)
    relayout(false, false)
  }

  override fun doLayout() {
    try {
      val labels: Collection<TabLabel> = infoToLabel.values
      for (each in labels) {
        each.setTabActionsAutoHide(myTabLabelActionsAutoHide)
      }
      val moreBoundsBeforeLayout = moreToolbar!!.component.bounds
      val entryPointBoundsBeforeLayout = if (entryPointToolbar != null) entryPointToolbar!!.component.bounds else Rectangle(0, 0, 0, 0)
      headerFitSize = computeHeaderFitSize()
      val visible: MutableList<TabInfo?> = ArrayList(
        visibleInfos)
      if (dropInfo != null && !visible.contains(dropInfo) && showDropLocation) {
        if (dropInfoIndex >= 0 && dropInfoIndex < visible.size) {
          visible.add(dropInfoIndex, dropInfo)
        }
        else {
          visible.add(dropInfo)
        }
      }
      if (effectiveLayout is SingleRowLayout) {
        singleRowLayout.scrollSelectionInView()
        lastLayoutPass = singleRowLayout.layoutSingleRow(visible)
        val titleRect = titleRect
        if (titleRect != null && !titleRect.isEmpty) {
          val preferredSize = titleWrapper.preferredSize
          val bounds = Rectangle(titleRect)
          JBInsets.removeFrom(bounds, layoutInsets)
          val xDiff = (bounds.width - preferredSize.width) / 2
          val yDiff = (bounds.height - preferredSize.height) / 2
          bounds.x += xDiff
          bounds.width -= 2 * xDiff
          bounds.y += yDiff
          bounds.height -= 2 * yDiff
          titleWrapper.bounds = bounds
        }
        else {
          titleWrapper.bounds = Rectangle()
        }
        tableLayout.myLastTableLayout = null
        val divider = mySplitter.divider
        if (divider.parent === this) {
          val location = if (tabsPosition == JBTabsPosition.left) singleRowLayout.myLastSingRowLayout.tabRectangle.width else width - singleRowLayout.myLastSingRowLayout.tabRectangle.width
          divider.setBounds(location, 0, 1, height)
        }
      }
      else {
        tableLayout.scrollSelectionInView()
        lastLayoutPass = tableLayout.layoutTable(visible)
        singleRowLayout.myLastSingRowLayout = null
      }
      centerizeEntryPointToolbarPosition()
      centerizeMoreToolbarPosition()
      moveDraggedTabLabel()
      tabActionsAutoHideListener.processMouseOver()
      applyResetComponents()
      scrollBar.orientation = if (isHorizontalTabs) Adjustable.HORIZONTAL else Adjustable.VERTICAL
      scrollBar.bounds = scrollBarBounds
      updateScrollBarModel()
      updateToolbarIfVisibilityChanged(moreToolbar, moreBoundsBeforeLayout)
      updateToolbarIfVisibilityChanged(entryPointToolbar, entryPointBoundsBeforeLayout)
    }
    finally {
      forcedRelayout = false
    }
  }

  private val tabsAreaWidth: Int
    get() {
      if (moreToolbar!!.component.isVisible) {
        return moreToolbar.component.x
      }
      else if (entryPointToolbar != null && entryPointToolbar!!.component.isVisible && tableLayout.myLastTableLayout == null) {
        return entryPointToolbar!!.component.x
      }
      return bounds.width
    }

  private fun centerizeMoreToolbarPosition() {
    val moreRect = moreRect
    val mComponent = moreToolbar!!.component
    if (moreRect != null && !moreRect.isEmpty) {
      val bounds = Rectangle(moreRect)
      if (!ExperimentalUI.isNewUI() || !tabsPosition.isSide) {
        val preferredSize = mComponent.preferredSize
        val xDiff = (bounds.width - preferredSize.width) / 2
        val yDiff = (bounds.height - preferredSize.height) / 2
        bounds.x += xDiff + 2
        bounds.width -= 2 * xDiff
        bounds.y += yDiff
        bounds.height -= 2 * yDiff
      }
      mComponent.bounds = bounds
    }
    else {
      mComponent.bounds = Rectangle()
    }
    mComponent.putClientProperty(LAYOUT_DONE, true)
  }

  private fun centerizeEntryPointToolbarPosition() {
    val eComponent = (if (entryPointToolbar == null) null else entryPointToolbar!!.component) ?: return
    val entryPointRect = entryPointRect
    if (entryPointRect != null && !entryPointRect.isEmpty && tabCount > 0) {
      val preferredSize = eComponent.preferredSize
      val bounds = Rectangle(entryPointRect)
      if (!ExperimentalUI.isNewUI() || !tabsPosition.isSide) {
        val xDiff = (bounds.width - preferredSize.width) / 2
        val yDiff = (bounds.height - preferredSize.height) / 2
        bounds.x += xDiff + 2
        bounds.width -= 2 * xDiff
        bounds.y += yDiff
        bounds.height -= 2 * yDiff
      }
      eComponent.bounds = bounds
    }
    else {
      eComponent.bounds = Rectangle()
    }
    eComponent.putClientProperty(LAYOUT_DONE, true)
  }

  fun moveDraggedTabLabel() {
    val dragHelper = dragHelper
    if (dragHelper?.dragRec != null) {
      val selectedLabel = infoToLabel[draggedTabSelectionInfo]
      if (selectedLabel != null) {
        val bounds = selectedLabel.bounds
        if (isHorizontalTabs) {
          selectedLabel.setBounds(dragHelper.dragRec.x, bounds.y, bounds.width, bounds.height)
        }
        else {
          selectedLabel.setBounds(bounds.x, dragHelper.dragRec.y, bounds.width, bounds.height)
        }
      }
    }
  }

  protected open val draggedTabSelectionInfo: TabInfo?
    get() = selectedInfo

  private fun computeHeaderFitSize(): Dimension {
    val max = computeMaxSize()
    if (position == JBTabsPosition.top || position == JBTabsPosition.bottom) {
      return Dimension(size.width, if (horizontalSide) Math.max(max.myLabel.height, max.myToolbar.height) else max.myLabel.height)
    }
    else {
      return Dimension(max.myLabel.width + if (horizontalSide) 0 else max.myToolbar.width, size.height)
    }
  }

  fun layoutComp(componentX: Int, componentY: Int, component: JComponent, deltaWidth: Int, deltaHeight: Int): Rectangle {
    return layoutComp(bounds = Rectangle(componentX, componentY, width, height),
                      component = component,
                      deltaWidth = deltaWidth,
                      deltaHeight = deltaHeight)
  }

  fun layoutComp(bounds: Rectangle, component: JComponent, deltaWidth: Int, deltaHeight: Int): Rectangle {
    val insets = layoutInsets
    val inner = innerInsets
    val x = insets.left + bounds.x + inner.left
    val y = insets.top + bounds.y + inner.top
    var width = bounds.width - insets.left - insets.right - bounds.x - inner.left - inner.right
    var height = bounds.height - insets.top - insets.bottom - bounds.y - inner.top - inner.bottom
    if (!isHideTabs) {
      width += deltaWidth
      height += deltaHeight
    }
    return layout(component = component, x = x, y = y, width = width, height = height)
  }

  override fun setInnerInsets(innerInsets: Insets): JBTabsPresentation {
    this.innerInsets = innerInsets
    return this
  }

  val layoutInsets: Insets
    get() = myBorder.effectiveBorder
  open val toolbarInset: Int
    get() = arcSize + 1

  fun resetLayout(resetLabels: Boolean) {
    for (each in myVisibleInfos) {
      reset(each, resetLabels)
    }
    if (dropInfo != null) {
      reset(dropInfo!!, resetLabels)
    }
    for (each in myHiddenInfos.keys) {
      reset(each, resetLabels)
    }
    for (eachDeferred in myDeferredToRemove.keys) {
      resetLayout(eachDeferred as JComponent)
    }
  }

  private fun reset(each: TabInfo, resetLabels: Boolean) {
    val c = each.component
    if (c != null) {
      resetLayout(c)
    }
    resetLayout(infoToToolbar[each])
    if (resetLabels) {
      resetLayout(infoToLabel[each])
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (myVisibleInfos.isEmpty()) {
      if (emptyText != null) {
        setupAntialiasing(g)
        UIUtil.drawCenteredString((g as Graphics2D), Rectangle(0, 0, width, height), emptyText!!)
      }
      return
    }
    tabPainter.fillBackground((g as Graphics2D), Rectangle(0, 0, width, height))
    drawBorder(g)
    drawToolbarSeparator(g)
  }

  private fun drawToolbarSeparator(g: Graphics) {
    val toolbar = infoToToolbar[selectedInfo]
    if (toolbar != null && toolbar.parent === this && !isSideComponentOnTabs && !horizontalSide && isHideTabs) {
      val bounds = toolbar.bounds
      if (bounds.width > 0) {
        if (isSideComponentBefore) {
          tabPainter.paintBorderLine((g as Graphics2D), separatorWidth,
                                     Point(bounds.x + bounds.width, bounds.y),
                                     Point(bounds.x + bounds.width, bounds.y + bounds.height))
        }
        else {
          tabPainter.paintBorderLine((g as Graphics2D), separatorWidth,
                                     Point(bounds.x - separatorWidth, bounds.y),
                                     Point(bounds.x - separatorWidth, bounds.y + bounds.height))
        }
      }
    }
  }

  val selectedLabel: TabLabel?
    get() = infoToLabel.get(selectedInfo)

  open val visibleInfos: List<TabInfo>
    get() {
      if (AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
        groupPinnedFirst(myVisibleInfos)
      }
      if (isAlphabeticalMode()) {
        val result = ArrayList(myVisibleInfos)
        sortTabsAlphabetically(result)
        return result
      }
      return myVisibleInfos
    }

  private fun groupPinnedFirst(infos: MutableList<TabInfo>) {
    var firstNotPinned = -1
    var changed = false
    for (i in infos.indices) {
      val info = infos[i]
      if (info.isPinned) {
        if (firstNotPinned != -1) {
          val tabInfo = infos.removeAt(firstNotPinned)
          infos.add(firstNotPinned, info)
          infos[i] = tabInfo
          firstNotPinned++
          changed = true
        }
      }
      else if (firstNotPinned == -1) {
        firstNotPinned = i
      }
    }
    if (changed) resetTabsCache()
  }

  private val isNavigationVisible: Boolean
    get() = myVisibleInfos.size > 1

  override fun getComponentGraphics(graphics: Graphics): Graphics {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
  }

  protected fun drawBorder(g: Graphics?) {
    if (!isHideTabs) {
      myBorder.paintBorder(this, g, 0, 0, width, height)
    }
  }

  private fun computeMaxSize(): Max {
    val max = Max()
    val isSideComponentOnTabs = effectiveLayout!!.isSideComponentOnTabs
    for (eachInfo in myVisibleInfos) {
      val label = infoToLabel[eachInfo]
      max.myLabel.height = Math.max(max.myLabel.height, label!!.preferredSize.height)
      max.myLabel.width = Math.max(max.myLabel.width, label.preferredSize.width)
      if (isSideComponentOnTabs) {
        val toolbar = infoToToolbar[eachInfo]
        if (toolbar != null && !toolbar.isEmpty) {
          max.myToolbar.height = Math.max(max.myToolbar.height, toolbar.preferredSize.height)
          max.myToolbar.width = Math.max(max.myToolbar.width, toolbar.preferredSize.width)
        }
      }
    }
    if (tabsPosition.isSide) {
      if (mySplitter.sideTabsLimit > 0) {
        max.myLabel.width = Math.min(max.myLabel.width, mySplitter.sideTabsLimit)
      }
    }
    max.myToolbar.height++
    return max
  }

  override fun getMinimumSize(): Dimension {
    return computeSize(
      { component: JComponent -> component.minimumSize }, 1)
  }

  override fun getPreferredSize(): Dimension {
    return computeSize(
      { component: JComponent -> component.preferredSize }, 3)
  }

  private fun computeSize(transform: Function<in JComponent, out Dimension>, tabCount: Int): Dimension {
    val size = Dimension()
    for (each in myVisibleInfos) {
      val c = each.component
      if (c != null) {
        val eachSize = transform.`fun`(c)
        size.width = Math.max(eachSize.width, size.width)
        size.height = Math.max(eachSize.height, size.height)
      }
    }
    addHeaderSize(size, tabCount)
    return size
  }

  private fun addHeaderSize(size: Dimension, tabsCount: Int) {
    val header = computeHeaderPreferredSize(tabsCount)
    val horizontal = tabsPosition == JBTabsPosition.top || tabsPosition == JBTabsPosition.bottom
    if (horizontal) {
      size.height += header.height
      size.width = Math.max(size.width, header.width)
    }
    else {
      size.height += Math.max(size.height, header.height)
      size.width += header.width
    }
    val insets = layoutInsets
    size.width += insets.left + insets.right + 1
    size.height += insets.top + insets.bottom + 1
  }

  private fun computeHeaderPreferredSize(tabsCount: Int): Dimension {
    val infos: Iterator<TabInfo?> = infoToLabel.keys.iterator()
    val size = Dimension()
    var currentTab = 0
    val horizontal = tabsPosition == JBTabsPosition.top || tabsPosition == JBTabsPosition.bottom
    while (infos.hasNext()) {
      val canGrow = currentTab < tabsCount
      val eachInfo = infos.next()
      val eachLabel = infoToLabel[eachInfo]
      val eachPrefSize = eachLabel!!.preferredSize
      if (horizontal) {
        if (canGrow) {
          size.width += eachPrefSize.width
        }
        size.height = Math.max(size.height, eachPrefSize.height)
      }
      else {
        size.width = Math.max(size.width, eachPrefSize.width)
        if (canGrow) {
          size.height += eachPrefSize.height
        }
      }
      currentTab++
    }
    if (horizontal) {
      size.height += myBorder.thickness
    }
    else {
      size.width += myBorder.thickness
    }
    return size
  }

  override fun getTabCount(): Int {
    return tabs.size
  }

  override fun getPresentation(): JBTabsPresentation {
    return this
  }

  override fun removeTab(info: TabInfo?): ActionCallback {
    return doRemoveTab(info, null, false)
  }

  override fun removeTab(info: TabInfo, forcedSelectionTransfer: TabInfo?) {
    doRemoveTab(info, forcedSelectionTransfer, false)
  }

  @RequiresEdt
  private fun doRemoveTab(info: TabInfo?, forcedSelectionTransfer: TabInfo?, isDropTarget: Boolean): ActionCallback {
    if (removeNotifyInProgress) {
      LOG.warn(IllegalStateException("removeNotify in progress"))
    }
    if (popupInfo == info) popupInfo = null
    if (!isDropTarget) {
      if (info == null || !tabs.contains(info)) return ActionCallback.DONE
    }
    if (isDropTarget && lastLayoutPass != null) {
      lastLayoutPass!!.myVisibleInfos.remove(info)
    }
    val result = ActionCallback()
    val toSelect: TabInfo?
    toSelect = if (forcedSelectionTransfer == null) {
      getToSelectOnRemoveOf(info!!)
    }
    else {
      assert(myVisibleInfos.contains(forcedSelectionTransfer)) { "Cannot find tab for selection transfer, tab=$forcedSelectionTransfer" }
      forcedSelectionTransfer
    }
    if (toSelect != null) {
      val clearSelection = info == mySelectedInfo
      val transferFocus = isFocused(info!!)
      processRemove(info, false)
      if (clearSelection) {
        setSelectedInfo(info)
      }
      _setSelected(toSelect, transferFocus, true).doWhenProcessed { removeDeferred().notifyWhenDone(result) }
    }
    else {
      processRemove(info, true)
      removeDeferred().notifyWhenDone(result)
    }
    if (myVisibleInfos.isEmpty()) {
      removeDeferredNow()
    }
    revalidateAndRepaint(true)
    fireTabRemoved(info!!)
    return result
  }

  // Tells whether focus is currently within one of the tab's components, or it was there last time the containing window had focus
  private fun isFocused(info: TabInfo): Boolean {
    val label = infoToLabel[info]
    val toolbar = infoToToolbar[info]
    val component = info.component
    val ancestorChecker = Predicate<Component?> { focusOwner ->
      @Suppress("NAME_SHADOWING")
      var focusOwner = focusOwner
      while (focusOwner != null) {
        if (focusOwner === label || focusOwner === toolbar || focusOwner === component) {
          return@Predicate true
        }
        focusOwner = focusOwner.parent
      }
      false
    }
    if (ancestorChecker.test(KeyboardFocusManager.getCurrentKeyboardFocusManager().permanentFocusOwner)) {
      return true
    }
    val ourWindow = SwingUtilities.getWindowAncestor(this)
    return ourWindow != null && !ourWindow.isFocused && ancestorChecker.test(ourWindow.mostRecentFocusOwner)
  }

  private fun processRemove(info: TabInfo?, forcedNow: Boolean) {
    val tabLabel = infoToLabel[info]
    tabLabel?.let { remove(it) }
    val toolbar = infoToToolbar[info]
    toolbar?.let { remove(it) }
    val tabComponent = info!!.component
    if (forcedNow || !isToDeferRemoveForLater(tabComponent)) {
      remove(tabComponent)
    }
    else {
      addToDeferredRemove(tabComponent)
    }
    myVisibleInfos.remove(info)
    myHiddenInfos.remove(info)
    infoToLabel.remove(info)
    infoToPage.remove(info)
    infoToToolbar.remove(info)
    if (tabLabelAtMouse === tabLabel) {
      tabLabelAtMouse = null
    }
    resetTabsCache()
    updateAll(false)
  }

  override fun findInfo(component: Component): TabInfo? {
    for (each in tabs) {
      if (each.component === component) {
        return each
      }
    }
    return null
  }

  override fun findInfo(event: MouseEvent): TabInfo? {
    val point = SwingUtilities.convertPoint(event.component, event.point, this)
    return _findInfo(point, false)
  }

  override fun findInfo(`object`: Any): TabInfo? {
    for (i in 0 until tabCount) {
      val each = getTabAt(i)
      val eachObject = each.getObject()
      if (eachObject != null && eachObject == `object`) {
        return each
      }
    }
    return null
  }

  private fun _findInfo(point: Point, labelsOnly: Boolean): TabInfo? {
    var component = findComponentAt(point)
    while (component !== this) {
      if (component == null) return null
      if (component is TabLabel) {
        return component.info
      }
      if (!labelsOnly) {
        val info = findInfo(component)
        if (info != null) return info
      }
      component = component.parent
    }
    return null
  }

  override fun removeAllTabs() {
    for (each in tabs) {
      removeTab(each)
    }
  }

  private class Max {
    val myLabel = Dimension()
    val myToolbar = Dimension()
  }

  private fun updateContainer(forced: Boolean, layoutNow: Boolean) {
    for (tabInfo in java.util.List.copyOf(myVisibleInfos)) {
      val component = tabInfo.component
      if (tabInfo == selectedInfo) {
        unqueueFromRemove(component)
        val parent = component.parent
        if (parent != null && parent !== this) {
          parent.remove(component)
        }
        if (component.parent == null) {
          add(component)
        }
      }
      else {
        if (component.parent == null) {
          continue
        }
        if (isToDeferRemoveForLater(component)) {
          addToDeferredRemove(component)
        }
        else {
          remove(component)
        }
      }
    }
    updateEntryPointToolbar()
    if (effectiveLayout === singleRowLayout) {
      singleRowLayout.scrollSelectionInView()
    }
    else if (effectiveLayout === tableLayout) {
      tableLayout.scrollSelectionInView()
    }
    relayout(forced, layoutNow)
  }

  private fun updateEntryPointToolbar() {
    if (entryPointToolbar != null) {
      remove(entryPointToolbar!!.component)
    }
    val selectedInfo = selectedInfo
    val tabActionGroup = selectedInfo?.tabPaneActions
    val entryPointActionGroup = entryPointActionGroup
    if (tabActionGroup != null || entryPointActionGroup != null) {
      val group: ActionGroup
      group = if (tabActionGroup != null && entryPointActionGroup != null) {
        // check that more toolbar and entry point toolbar are in the same row
        if (!moreToolbar!!.component.bounds.isEmpty &&
            tabActionGroup.getChildren(null).isNotEmpty() &&
            (!TabLayout.showPinnedTabsSeparately() || tabs.all { !it.isPinned })) {
          DefaultActionGroup(FakeEmptyAction(), Separator.create(), tabActionGroup, Separator.create(), entryPointActionGroup)
        }
        else {
          DefaultActionGroup(tabActionGroup, Separator.create(), entryPointActionGroup)
        }
      }
      else {
        tabActionGroup ?: entryPointActionGroup!!
      }
      if (entryPointToolbar != null && entryPointToolbar!!.actionGroup == group) {
        // keep old toolbar to avoid blinking (actions need to be updated to be visible)
        add(entryPointToolbar!!.component)
      }
      else {
        entryPointToolbar = createToolbar(group)
        add(entryPointToolbar!!.component)
      }
    }
    else {
      entryPointToolbar = null
    }
  }

  val actionsInsets: Insets
    /**
     * @return insets, that should be used to layout [JBTabsImpl.moreToolbar] and [JBTabsImpl.entryPointToolbar]
     */
    get() = if (ExperimentalUI.isNewUI()) {
      if (position.isSide) JBInsets.create(Insets(4, 8, 4, 3)) else JBInsets.create(Insets(0, 5, 0, 8))
    }
    else {
      JBInsets.create(Insets(0, 1, 0, 1))
    }

  // Useful when you need to always show separator as first or last component of ActionToolbar
  // Just put it as first or last action and any separator will not be counted as corner and will be shown
  private class FakeEmptyAction : DumbAwareAction(), CustomComponentAction {
    override fun actionPerformed(e: AnActionEvent) {
      // do nothing
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      val panel = JPanel()
      val size = Dimension(0, 0)
      panel.preferredSize = size
      return panel
    }
  }

  override fun addImpl(comp: Component, constraints: Any, index: Int) {
    unqueueFromRemove(comp)
    if (comp is TabLabel) {
      comp.apply(if (uiDecorator != null) uiDecorator!!.decoration else defaultDecorator.decoration)
    }
    super.addImpl(comp, constraints, index)
  }

  fun relayout(forced: Boolean, layoutNow: Boolean) {
    if (!forcedRelayout) {
      forcedRelayout = forced
    }
    if (moreToolbar != null) {
      moreToolbar.component.isVisible = !isHideTabs &&
                                          (effectiveLayout is ScrollableSingleRowLayout ||
                                           effectiveLayout is TableLayout)
    }
    revalidateAndRepaint(layoutNow)
  }

  val borderThickness: Int
    get() = myBorder.thickness

  override fun addTabMouseListener(listener: MouseListener): JBTabs {
    removeListeners()
    myTabMouseListeners.add(listener)
    addListeners()
    return this
  }

  override fun getComponent(): JComponent {
    return this
  }

  private fun addListeners() {
    for (eachInfo in myVisibleInfos) {
      val label = infoToLabel[eachInfo]
      for (eachListener in myTabMouseListeners) {
        when (eachListener) {
          is MouseListener -> label!!.addMouseListener(eachListener)
          is MouseMotionListener -> label!!.addMouseMotionListener(eachListener)
          else -> assert(false)
        }
      }
    }
  }

  private fun removeListeners() {
    for (eachInfo in myVisibleInfos) {
      val label = infoToLabel[eachInfo]
      for (eachListener in myTabMouseListeners) {
        when (eachListener) {
          is MouseListener -> label!!.removeMouseListener(eachListener)
          is MouseMotionListener -> label!!.removeMouseMotionListener(eachListener)
          else -> assert(false)
        }
      }
    }
  }

  private fun updateListeners() {
    removeListeners()
    addListeners()
  }

  override fun addListener(listener: TabsListener): JBTabs = addListener(listener = listener, disposable = null)

  override fun addListener(listener: TabsListener, disposable: Disposable?): JBTabs {
    myTabListeners.add(listener)
    if (disposable != null) {
      Disposer.register(disposable) { myTabListeners.remove(listener) }
    }
    return this
  }

  override fun setSelectionChangeHandler(handler: JBTabs.SelectionChangeHandler): JBTabs {
    mySelectionChangeHandler = handler
    return this
  }

  fun setFocused(focused: Boolean) {
    if (myFocused == focused) return
    myFocused = focused
    if (myPaintFocus) {
      repaint()
    }
  }

  override fun getIndexOf(tabInfo: TabInfo?): Int {
    return visibleInfos.indexOf(tabInfo)
  }

  override fun isHideTabs(): Boolean {
    return myHideTabs || myHideTopPanel
  }

  override fun setHideTabs(hideTabs: Boolean) {
    if (isHideTabs == hideTabs) return
    myHideTabs = hideTabs
    if (entryPointToolbar != null) {
      entryPointToolbar!!.component.isVisible = !myHideTabs
    }
    relayout(forced = true, layoutNow = false)
  }

  override var isHideTopPanel: Boolean = false
    set(value) {
      if (field == value) {
        return
      }
      field = value
      for (tab in tabs) {
        tab.sideComponent.isVisible = !field
      }
      relayout(forced = true, layoutNow = true)
    }

  override fun setActiveTabFillIn(color: Color?): JBTabsPresentation {
    if (!isChanged(myActiveTabFillIn, color)) return this
    myActiveTabFillIn = color
    revalidateAndRepaint(false)
    return this
  }

  override fun setTabLabelActionsAutoHide(autoHide: Boolean): JBTabsPresentation {
    if (myTabLabelActionsAutoHide != autoHide) {
      myTabLabelActionsAutoHide = autoHide
      revalidateAndRepaint(false)
    }
    return this
  }

  override fun setFocusCycle(root: Boolean): JBTabsPresentation {
    isFocusCycleRoot = root
    return this
  }

  override fun setPaintFocus(paintFocus: Boolean): JBTabsPresentation {
    myPaintFocus = paintFocus
    return this
  }

  private abstract class BaseNavigationAction(copyFromId: @NlsSafe String,
                                              private val tabs: JBTabsImpl,
                                              parentDisposable: Disposable) : DumbAwareAction() {
    private val myShadow: ShadowAction

    init {
      @Suppress("LeakingThis")
      myShadow = ShadowAction(this, copyFromId, tabs, parentDisposable)
      isEnabledInModalContext = true
    }

    override fun update(e: AnActionEvent) {
      var tabs = e.getData(JBTabsEx.NAVIGATION_ACTIONS_KEY) as JBTabsImpl?
      e.presentation.isVisible = tabs != null
      if (tabs == null) return
      tabs = findNavigatableTabs(tabs)
      e.presentation.isEnabled = tabs != null
      if (tabs != null) {
        _update(e = e, tabs = tabs, selectedIndex = tabs.visibleInfos.indexOf(tabs.selectedInfo))
      }
    }

    fun findNavigatableTabs(tabs: JBTabsImpl?): JBTabsImpl? {
      // The debugger UI contains multiple nested JBTabsImpl, where the innermost JBTabsImpl has only one tab. In this case,
      // the action should target the outer JBTabsImpl.
      if (tabs == null || tabs !== this.tabs) {
        return null
      }
      if (tabs.isNavigatable) {
        return tabs
      }
      var c: Component? = tabs.parent
      while (c != null) {
        if (c is JBTabsImpl && c.isNavigatable) {
          return c
        }
        c = c.parent
      }
      return null
    }

    fun reconnect(actionId: String?) {
      myShadow.reconnect(ActionManager.getInstance().getAction(actionId!!))
    }

    protected abstract fun _update(e: AnActionEvent, tabs: JBTabsImpl, selectedIndex: Int)
    override fun actionPerformed(e: AnActionEvent) {
      var tabs = e.getData(JBTabsEx.NAVIGATION_ACTIONS_KEY) as JBTabsImpl?
      tabs = findNavigatableTabs(tabs) ?: return

      var infos: List<TabInfo?>
      var index: Int
      while (true) {
        infos = tabs!!.visibleInfos
        index = infos.indexOf(tabs.selectedInfo)
        if (index == -1) {
          return
        }

        if (borderIndex(infos, index) && tabs.navigatableParent() != null) {
          tabs = tabs.navigatableParent()
        }
        else {
          break
        }
      }
      _actionPerformed(e = e, tabs = tabs, selectedIndex = index)
    }

    protected abstract fun borderIndex(infos: List<TabInfo?>, index: Int): Boolean
    protected abstract fun _actionPerformed(e: AnActionEvent?, tabs: JBTabsImpl?, selectedIndex: Int)
  }

  private class SelectNextAction(tabs: JBTabsImpl, parentDisposable: Disposable) : BaseNavigationAction(IdeActions.ACTION_NEXT_TAB, tabs,
                                                                                                        parentDisposable) {
    override fun _update(e: AnActionEvent, tabs: JBTabsImpl, selectedIndex: Int) {
      e.presentation.isEnabled = tabs.findEnabledForward(selectedIndex, true) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun borderIndex(infos: List<TabInfo?>, index: Int): Boolean = index == infos.size - 1

    override fun _actionPerformed(e: AnActionEvent?, tabs: JBTabsImpl?, selectedIndex: Int) {
      val tabInfo = tabs!!.findEnabledForward(selectedIndex, true) ?: return
      val lastFocus = tabInfo.lastFocusOwner
      tabs.select(tabInfo, true)
      for (nestedTabs in tabs.nestedTabs) {
        if (lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs)) {
          nestedTabs.selectFirstVisible()
        }
      }
    }
  }

  protected val isNavigatable: Boolean
    get() {
      val selectedIndex = visibleInfos.indexOf(selectedInfo)
      return isNavigationVisible && selectedIndex >= 0 && navigationActionsEnabled
    }

  private fun navigatableParent(): JBTabsImpl? {
    var c: Component? = parent
    while (c != null) {
      if (c is JBTabsImpl && c.isNavigatable) {
        return c
      }
      c = c.parent
    }
    return null
  }

  private fun selectFirstVisible() {
    if (!isNavigatable) {
      return
    }

    val select = visibleInfos.get(0)
    val lastFocus = select.lastFocusOwner
    select(select, true)
    for (nestedTabs in nestedTabs) {
      if (lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs)) {
        nestedTabs.selectFirstVisible()
      }
    }
  }

  private fun selectLastVisible() {
    if (!isNavigatable) {
      return
    }

    val last = visibleInfos.size - 1
    val select = visibleInfos.get(last)
    val lastFocus = select.lastFocusOwner
    select(select, true)
    for (nestedTabs in nestedTabs) {
      if (lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs)) {
        nestedTabs.selectLastVisible()
      }
    }
  }

  private class SelectPreviousAction(tabs: JBTabsImpl, parentDisposable: Disposable) : BaseNavigationAction(IdeActions.ACTION_PREVIOUS_TAB,
                                                                                                            tabs, parentDisposable) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun _update(e: AnActionEvent, tabs: JBTabsImpl, selectedIndex: Int) {
      e.presentation.isEnabled = tabs.findEnabledBackward(selectedIndex, true) != null
    }

    override fun borderIndex(infos: List<TabInfo?>, index: Int): Boolean = index == 0

    override fun _actionPerformed(e: AnActionEvent?, tabs: JBTabsImpl?, selectedIndex: Int) {
      val tabInfo = tabs!!.findEnabledBackward(selectedIndex, true) ?: return
      val lastFocus = tabInfo.lastFocusOwner
      tabs.select(tabInfo, true)
      for (nestedTabs in tabs.nestedTabs) {
        if (lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs)) {
          nestedTabs.selectLastVisible()
        }
      }
    }
  }

  private fun disposePopupListener() {
    if (activePopup != null) {
      activePopup!!.removePopupMenuListener(popupListener)
      activePopup = null
    }
  }

  override fun setSideComponentVertical(vertical: Boolean): JBTabsPresentation {
    horizontalSide = !vertical
    for (each in myVisibleInfos) {
      each.changeSupport.firePropertyChange(TabInfo.ACTION_GROUP, "new1", "new2")
    }
    relayout(true, false)
    return this
  }

  override fun setSideComponentOnTabs(onTabs: Boolean): JBTabsPresentation {
    isSideComponentOnTabs = onTabs
    relayout(true, false)
    return this
  }

  override fun setSideComponentBefore(before: Boolean): JBTabsPresentation {
    isSideComponentBefore = before
    relayout(true, false)
    return this
  }

  override fun setSingleRow(singleRow: Boolean): JBTabsPresentation {
    this.singleRow = singleRow
    updateRowLayout()
    return this
  }

  private fun setLayout(layout: TabLayout): Boolean {
    if (effectiveLayout === layout) return false
    effectiveLayout = layout
    return true
  }

  open fun useSmallLabels(): Boolean {
    return false
  }

  override fun isSingleRow(): Boolean {
    return singleRow
  }

  val isSideComponentVertical: Boolean
    get() = !horizontalSide

  override fun setUiDecorator(decorator: UiDecorator?): JBTabsPresentation {
    uiDecorator = decorator ?: defaultDecorator
    applyDecoration()
    return this
  }

  override fun setUI(newUI: ComponentUI) {
    super.setUI(newUI)
    applyDecoration()
  }

  override fun updateUI() {
    super.updateUI()
    SwingUtilities.invokeLater {
      applyDecoration()
      revalidateAndRepaint(false)
    }
  }

  private fun applyDecoration() {
    if (uiDecorator != null) {
      val uiDecoration = uiDecorator!!.decoration
      for (tabLabel in infoToLabel.values) {
        tabLabel.apply(uiDecoration)
      }
    }
    for (each in tabs) {
      adjust(each)
    }
    relayout(forced = true, layoutNow = false)
  }

  protected open fun adjust(tabInfo: TabInfo) {
    if (adjustBorders) {
      @Suppress("DEPRECATION")
      UIUtil.removeScrollBorder(tabInfo.component)
    }
  }

  override fun sortTabs(comparator: Comparator<TabInfo>) {
    myVisibleInfos.sortWith(comparator)
    resetTabsCache()
    relayout(forced = true, layoutNow = false)
  }

  protected fun reorderTab(tabInfo: TabInfo, newIndex: Int) {
    if (myVisibleInfos.remove(tabInfo)) {
      myVisibleInfos.add(newIndex, tabInfo)
      resetTabsCache()
      relayout(forced = true, layoutNow = false)
    }
  }

  override fun setRequestFocusOnLastFocusedComponent(requestFocusOnLastFocusedComponent: Boolean): JBTabsPresentation {
    isRequestFocusOnLastFocusedComponent = requestFocusOnLastFocusedComponent
    return this
  }

  override fun getData(dataId: @NonNls String): Any? {
    if (dataProvider != null) {
      val value = dataProvider!!.getData(dataId)
      if (value != null) return value
    }
    if (QuickActionProvider.KEY.`is`(dataId)) {
      return this
    }
    if (MorePopupAware.KEY.`is`(dataId)) {
      return this
    }
    return if (JBTabsEx.NAVIGATION_ACTIONS_KEY.`is`(dataId)) this else null
  }

  override fun getActions(originalProvider: Boolean): List<AnAction> {
    val result = ArrayList<AnAction>()
    val selection = selectedInfo
    if (selection != null) {
      val group = selection.group
      if (group != null) {
        val children = group.getChildren(null)
        Collections.addAll(result, *children)
      }
    }
    return result
  }

  val navigationActions: ActionGroup
    get() = myNavigationActions

  override fun getDataProvider(): DataProvider? = dataProvider

  override fun setDataProvider(dataProvider: DataProvider): JBTabsImpl {
    this.dataProvider = dataProvider
    return this
  }

  private class DefaultDecorator : UiDecorator {
    override fun getDecoration(): UiDecoration {
      return UiDecoration(null, JBInsets(5, 12, 5, 12))
    }
  }

  fun layout(component: JComponent, bounds: Rectangle): Rectangle {
    val now = component.bounds
    if (bounds != now) {
      component.bounds = bounds
    }
    component.doLayout()
    component.putClientProperty(LAYOUT_DONE, true)
    return bounds
  }

  fun layout(component: JComponent, x: Int, y: Int, width: Int, height: Int): Rectangle {
    return layout(component = component, bounds = Rectangle(x, y, width, height))
  }

  private fun applyResetComponents() {
    for (i in 0 until componentCount) {
      val each = getComponent(i)
      if (each is JComponent) {
        if (!ClientProperty.isTrue(each, LAYOUT_DONE)) {
          layout(each, Rectangle(0, 0, 0, 0))
        }
      }
    }
  }

  override fun setTabLabelActionsMouseDeadzone(length: TimedDeadzone.Length): JBTabsPresentation {
    tabActionsMouseDeadZone = length
    for (tabInfo in tabs) {
      infoToLabel.get(tabInfo)!!.updateTabActions()
    }
    return this
  }

  override fun setTabsPosition(position: JBTabsPosition): JBTabsPresentation {
    this.position = position
    val divider = mySplitter.divider
    if (position.isSide && divider.parent == null) {
      add(divider)
    }
    else if (divider.parent === this && !position.isSide) {
      remove(divider)
    }
    applyDecoration()
    relayout(true, false)
    return this
  }

  override fun getTabsPosition(): JBTabsPosition = position

  override fun setTabDraggingEnabled(enabled: Boolean): JBTabsPresentation {
    isTabDraggingEnabled = enabled
    return this
  }

  override fun setAlphabeticalMode(value: Boolean): JBTabsPresentation {
    alphabeticalMode = value
    return this
  }

  override fun setSupportsCompression(value: Boolean): JBTabsPresentation {
    supportsCompression = value
    updateRowLayout()
    return this
  }

  fun reallocate(source: TabInfo?, target: TabInfo?) {
    if (source == target || source == null || target == null) {
      return
    }
    val targetIndex = myVisibleInfos.indexOf(target)
    myVisibleInfos.remove(source)
    myVisibleInfos.add(targetIndex, source)
    invalidate()
    relayout(forced = true, layoutNow = true)
  }

  val isHorizontalTabs: Boolean
    get() = tabsPosition == JBTabsPosition.top || tabsPosition == JBTabsPosition.bottom

  override fun putInfo(info: MutableMap<in String, in String>) {
    selectedInfo?.putInfo(info)
  }

  override fun resetDropOver(tabInfo: TabInfo) {
    if (dropInfo != null) {
      val dropInfo = dropInfo!!
      this.dropInfo = null
      showDropLocation = true
      forcedRelayout = true
      dropInfoIndex = -1
      dropSide = -1
      doRemoveTab(info = dropInfo, forcedSelectionTransfer = null, isDropTarget = true)
    }
  }

  override fun startDropOver(tabInfo: TabInfo, point: RelativePoint): Image {
    dropInfo = tabInfo
    val pointInMySpace = point.getPoint(this)
    val index = effectiveLayout!!.getDropIndexFor(pointInMySpace)
    dropInfoIndex = index
    addTab(info = dropInfo!!, index = index, isDropTarget = true, fireEvents = true)
    val label = infoToLabel.get(dropInfo)
    val size = label!!.preferredSize
    label.setBounds(0, 0, size.width, size.height)
    val img = ImageUtil.createImage(/* gc = */ graphicsConfiguration, /* width = */ size.width, /* height = */ size.height, /* type = */
                                    BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    label.paintOffscreen(g)
    g.dispose()
    relayout(forced = true, layoutNow = false)
    return img
  }

  override fun processDropOver(over: TabInfo, point: RelativePoint) {
    val pointInMySpace = point.getPoint(this)
    val index = effectiveLayout!!.getDropIndexFor(pointInMySpace)
    val side: Int = if (myVisibleInfos.isEmpty()) {
      SwingConstants.CENTER
    }
    else {
      if (index != -1) -1 else effectiveLayout!!.getDropSideFor(pointInMySpace)
    }
    if (index != dropInfoIndex) {
      dropInfoIndex = index
      relayout(forced = true, layoutNow = false)
    }
    if (side != dropSide) {
      dropSide = side
      relayout(forced = true, layoutNow = false)
    }
  }

  override val isEmptyVisible: Boolean
    get() = myVisibleInfos.isEmpty()

  val tabHGap: Int
    get() = -myBorder.thickness

  override fun toString(): String = "JBTabs visible=$myVisibleInfos selected=$mySelectedInfo"

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleJBTabsImpl()
    }
    return accessibleContext
  }

  /**
   * Custom implementation of Accessible interface.  Given JBTabsImpl is similar
   * to the built-it JTabbedPane, we expose similar behavior. The one tricky part
   * is that JBTabsImpl can only expose the content of the selected tab, as the
   * content of tabs is created/deleted on demand when a tab is selected.
   */
  protected inner class AccessibleJBTabsImpl internal constructor() : AccessibleJComponent(), AccessibleSelection {
    init {
      accessibleComponent
      addListener(object : TabsListener {
        override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
          firePropertyChange(ACCESSIBLE_SELECTION_PROPERTY, null, null)
        }
      })
    }

    override fun getAccessibleName(): String {
      var name = accessibleName ?: getClientProperty(ACCESSIBLE_NAME_PROPERTY) as String?
      if (name == null) {
        // Similar to JTabbedPane, we return the name of our selected tab as our own name.
        val selectedLabel = selectedLabel
        if (selectedLabel != null && selectedLabel.accessibleContext != null) {
          name = selectedLabel.accessibleContext.accessibleName
        }
      }
      return name ?: super.getAccessibleName()
    }

    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PAGE_TAB_LIST

    override fun getAccessibleChild(i: Int): Accessible? {
      val accessibleChild = super.getAccessibleChild(i)
      // Note: Unlike a JTabbedPane, JBTabsImpl has many more child types than just pages.
      // So we wrap TabLabel instances with their corresponding AccessibleTabPage, while
      // leaving other types of children untouched.
      return if (accessibleChild is TabLabel) infoToPage.get(accessibleChild.info) else accessibleChild
    }

    override fun getAccessibleSelection(): AccessibleSelection = this

    override fun getAccessibleSelectionCount(): Int = if (selectedInfo == null) 0 else 1

    override fun getAccessibleSelection(i: Int): Accessible? {
      return infoToPage.get(selectedInfo ?: return null)
    }

    override fun isAccessibleChildSelected(i: Int): Boolean = i == getIndexOf(selectedInfo)

    override fun addAccessibleSelection(i: Int) {
      select(getTabAt(tabIndex = i), false)
    }

    override fun removeAccessibleSelection(i: Int) {
      // can't do
    }

    override fun clearAccessibleSelection() {
      // can't do
    }

    override fun selectAllAccessibleSelection() {
      // can't do
    }
  }

  @Deprecated("Not used.")
  fun dispose() {
  }
}

private fun getFocusOwner(): JComponent? {
  return KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JComponent
}

private fun updateToolbarIfVisibilityChanged(toolbar: ActionToolbar?, previousBounds: Rectangle) {
  if (toolbar == null) {
    return
  }

  val bounds = toolbar.component.bounds
  if (bounds.isEmpty != previousBounds.isEmpty) {
    toolbar.updateActionsImmediately()
  }
}

private val arcSize: Int
  get() = 4

private fun sortTabsAlphabetically(tabs: MutableList<TabInfo>) {
  val lastPinnedIndex = tabs.indexOfLast { it.isPinned }
  if (lastPinnedIndex == -1 || !AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
    tabs.sortWith(ABC_COMPARATOR)
  }
  else {
    tabs.subList(0, lastPinnedIndex + 1).sortWith(ABC_COMPARATOR)
    tabs.subList(lastPinnedIndex + 1, tabs.size).sortWith(ABC_COMPARATOR)
  }
}

/**
 * AccessibleContext implementation for a single tab page.
 *
 *
 * A tab page has a label as the display zone, name, description, etc.
 * A tab page exposes a child component only if it corresponds to the
 * selected tab in the tab pane. Inactive tabs don't have a child
 * component to expose, as components are created/deleted on demand.
 * A tab page exposes one action: select and activate the panel.
 */
private class AccessibleTabPage(private val parent: JBTabsImpl,
                                private val tabInfo: TabInfo) : AccessibleContext(), Accessible, AccessibleComponent, AccessibleAction {
  private val component = tabInfo.component

  init {
    setAccessibleParent(parent)
    initAccessibleContext()
  }

  private val tabIndex: Int
    get() = parent.getIndexOf(tabInfo)
  private val tabLabel: TabLabel?
    get() = parent.infoToLabel.get(tabInfo)

  /*
   * initializes the AccessibleContext for the page
   */
  fun initAccessibleContext() {
    // Note: null checks because we do not want to load Accessibility classes unnecessarily.
    if (component is Accessible) {
      val ac = component.getAccessibleContext()
      if (ac != null) {
        ac.accessibleParent = this
      }
    }
  }

  /////////////////
  // Accessibility support
  ////////////////
  override fun getAccessibleContext(): AccessibleContext {
    return this
  }

  // AccessibleContext methods
  override fun getAccessibleName(): String {
    var name = accessibleName
    if (name == null) {
      name = parent.getClientProperty(ACCESSIBLE_NAME_PROPERTY) as String?
    }
    if (name == null) {
      val label = tabLabel
      if (label != null && label.accessibleContext != null) {
        name = label.accessibleContext.accessibleName
      }
    }
    if (name == null) {
      name = super.getAccessibleName()
    }
    return name
  }

  override fun getAccessibleDescription(): String {
    var description = accessibleDescription
    if (description == null) {
      description = parent.getClientProperty(ACCESSIBLE_DESCRIPTION_PROPERTY) as String?
    }
    if (description == null) {
      val label = tabLabel
      if (label != null && label.accessibleContext != null) {
        description = label.accessibleContext.accessibleDescription
      }
    }
    if (description == null) {
      description = super.getAccessibleDescription()
    }
    return description
  }

  override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PAGE_TAB

  override fun getAccessibleStateSet(): AccessibleStateSet {
    val states = parent.accessibleContext.accessibleStateSet
    states.add(AccessibleState.SELECTABLE)
    val info = parent.selectedInfo
    if (info == tabInfo) {
      states.add(AccessibleState.SELECTED)
    }
    return states
  }

  override fun getAccessibleIndexInParent(): Int = tabIndex

  override fun getAccessibleChildrenCount(): Int {
    // Expose the tab content only if it is active, as the content for
    // inactive tab does is usually not ready (i.e. may never have been
    // activated).
    return if (parent.selectedInfo == tabInfo && component is Accessible) 1 else 0
  }

  override fun getAccessibleChild(i: Int): Accessible? {
    return if (parent.selectedInfo == tabInfo && component is Accessible) component else null
  }

  override fun getLocale(): Locale = parent.locale

  override fun getAccessibleComponent(): AccessibleComponent = this

  override fun getAccessibleAction(): AccessibleAction = this

  // AccessibleComponent methods
  override fun getBackground(): Color = parent.background

  override fun setBackground(c: Color) {
    parent.background = c
  }

  override fun getForeground(): Color = parent.foreground

  override fun setForeground(c: Color) {
    parent.foreground = c
  }

  override fun getCursor(): Cursor = parent.cursor

  override fun setCursor(c: Cursor) {
    parent.cursor = c
  }

  override fun getFont(): Font = parent.font

  override fun setFont(f: Font) {
    parent.font = f
  }

  override fun getFontMetrics(f: Font): FontMetrics = parent.getFontMetrics(f)

  override fun isEnabled(): Boolean = tabInfo.isEnabled

  override fun setEnabled(b: Boolean) {
    tabInfo.isEnabled = b
  }

  override fun isVisible(): Boolean = !tabInfo.isHidden

  override fun setVisible(b: Boolean) {
    tabInfo.isHidden = !b
  }

  override fun isShowing(): Boolean = parent.isShowing

  override fun contains(p: Point): Boolean {
    return bounds.contains(p)
  }

  override fun getLocationOnScreen(): Point {
    val parentLocation = parent.locationOnScreen
    val componentLocation = location
    componentLocation.translate(parentLocation.x, parentLocation.y)
    return componentLocation
  }

  override fun getLocation(): Point {
    val r = bounds
    return Point(r.x, r.y)
  }

  override fun setLocation(p: Point) {
    // do nothing
  }

  /**
   * Returns the bounds of tab.  The bounds are with respect to the JBTabsImpl coordinate space.
   */
  override fun getBounds(): Rectangle = tabLabel!!.bounds

  override fun setBounds(r: Rectangle) {
    // do nothing
  }

  override fun getSize(): Dimension {
    val r = bounds
    return Dimension(r.width, r.height)
  }

  override fun setSize(d: Dimension) {
    // do nothing
  }

  override fun getAccessibleAt(p: Point): Accessible? = if (component is Accessible) component else null

  override fun isFocusTraversable(): Boolean = false

  override fun requestFocus() {
    // do nothing
  }

  override fun addFocusListener(l: FocusListener) {
    // do nothing
  }

  override fun removeFocusListener(l: FocusListener) {
    // do nothing
  }

  override fun getAccessibleIcon(): Array<AccessibleIcon>? {
    return arrayOf((tabInfo.icon as? ImageIcon)?.accessibleContext as? AccessibleIcon ?: return null)
  }

  // AccessibleAction methods
  override fun getAccessibleActionCount(): Int = 1

  override fun getAccessibleActionDescription(i: Int): String? {
    return if (i == 0) "Activate" else null
  }

  override fun doAccessibleAction(i: Int): Boolean {
    if (i != 0) {
      return false
    }
    parent.select(info = tabInfo, requestFocus = true)
    return true
  }
}

private class TitleAction(private val tabs: JBTabsImpl,
                          private val titleProvider: () -> Pair<Icon, @Nls String>) : AnAction(), CustomComponentAction {
  private val label = object : JLabel() {
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      size.height = JBUI.scale(SingleHeightTabs.UNSCALED_PREF_HEIGHT)
      return size
    }

    override fun updateUI() {
      super.updateUI()
      font = TabLabel(tabs, TabInfo(null)).labelComponent.font
      border = JBUI.Borders.empty(0, 5, 0, 6)
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    update()
    return label
  }

  private fun update() {
    val pair = titleProvider()
    label.icon = pair.first
    label.text = pair.second
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    //do nothing
  }

  override fun update(e: AnActionEvent) {
    update()
  }
}
