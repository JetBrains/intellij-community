// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getBoolean
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
import com.intellij.openapi.util.text.StringUtil
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
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout
import com.intellij.ui.tabs.impl.singleRow.SingleRowPassInfo
import com.intellij.ui.tabs.impl.table.TableLayout
import com.intellij.ui.tabs.impl.themes.TabTheme
import com.intellij.util.Alarm
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.Producer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.*
import com.intellij.util.ui.StartupUiUtil.addAwtListener
import com.intellij.util.ui.update.LazyUiDisposable
import org.intellij.lang.annotations.MagicConstant
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

@DirtyUI
open class JBTabsImpl(private val myProject: Project?,
                      focusManager: IdeFocusManager?,
                      parentDisposable: Disposable) : JComponent(), JBTabsEx, PropertyChangeListener, TimerListener, DataProvider, PopupMenuListener, JBTabsPresentation, Queryable, UISettingsListener, QuickActionProvider, MorePopupAware, Accessible {
  private val myVisibleInfos: MutableList<TabInfo> = ArrayList()
  private val myInfo2Page: MutableMap<TabInfo?, AccessibleTabPage> = HashMap()
  private val myHiddenInfos: MutableMap<TabInfo, Int> = HashMap()
  private var mySelectedInfo: TabInfo? = null
  val myInfo2Label: MutableMap<TabInfo?, TabLabel> = HashMap()
  val myInfo2Toolbar: MutableMap<TabInfo?, Toolbar> = HashMap()
  val myMoreToolbar: ActionToolbar?
  var myEntryPointToolbar: ActionToolbar? = null
  val myTitleWrapper = NonOpaquePanel()
  var myHeaderFitSize: Dimension? = null
  private var innerInsets: Insets = JBInsets.emptyInsets()
  private val myTabMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList<EventListener>()
  private val myTabListeners = ContainerUtil.createLockFreeCopyOnWriteList<TabsListener>()
  private var myFocused = false
  private var myPopupGroup: Supplier<out ActionGroup>? = null
  var popupPlace: String? = null
    private set
  var myPopupInfo: TabInfo? = null
  val myNavigationActions: DefaultActionGroup
  val myPopupListener: PopupMenuListener
  var myActivePopup: JPopupMenu? = null
  var myHorizontalSide = true
  var isSideComponentOnTabs = true
    private set
  var isSideComponentBefore = true
    private set
  val separatorWidth = JBUI.scale(1)
  private var myDataProvider: DataProvider? = null
  private val myDeferredToRemove = WeakHashMap<Component, Component>()
  private var mySingleRowLayout: SingleRowLayout
  private var myTableLayout = createTableLayout()

  // it's an invisible splitter intended for changing size of tab zone
  private val mySplitter = TabsSideSplitter(this)
  var effectiveLayout: TabLayout? = null
    private set
  var lastLayoutPass: LayoutPassInfo? = null
    private set
  var myForcedRelayout = false
  private var myUiDecorator: UiDecorator? = null
  private var myPaintFocus = false
  private var myHideTabs = false
  private var myHideTopPanel = false
  private var isRequestFocusOnLastFocusedComponent = false
  private var myListenerAdded = false
  val myAttractions: MutableSet<TabInfo> = HashSet()
  private val myAnimator: Animator
  private var myAllTabs: List<TabInfo>? = null
  private var myFocusManager: IdeFocusManager
  private val myNestedTabs: MutableSet<JBTabsImpl> = HashSet()
  var myAddNavigationGroup = true
  private var myActiveTabFillIn: Color? = null
  private var myTabLabelActionsAutoHide = false
  private val myTabActionsAutoHideListener = TabActionsAutoHideListener()
  private var myTabActionsAutoHideListenerDisposable = Disposer.newDisposable()
  private var myGlassPane: IdeGlassPane? = null
  var tabActionsMouseDeadzone = TimedDeadzone.DEFAULT
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
  private var myNavigationActionsEnabled = true
  protected var myDropInfo: TabInfo? = null
  private var myDropInfoIndex = 0

  @MagicConstant(
    intValues = [SwingConstants.CENTER.toLong(), SwingConstants.TOP.toLong(), SwingConstants.LEFT.toLong(), SwingConstants.BOTTOM.toLong(), SwingConstants.RIGHT.toLong(), -1])
  private var myDropSide = -1
  protected var myShowDropLocation = true
  private var myOldSelection: TabInfo? = null
  private var mySelectionChangeHandler: JBTabs.SelectionChangeHandler? = null
  private var myDeferredFocusRequest: Runnable? = null
  private var myFirstTabOffset = 0
  val tabPainterAdapter = createTabPainterAdapter()
  val tabPainter = tabPainterAdapter.tabPainter
  open var isAlphabeticalMode = false
    private set
  private var mySupportsCompression = false
  private var myEmptyText: String? = null
  var isMouseInsideTabsArea = false
    private set
  private var myRemoveNotifyInProgress = false
  private var mySingleRow = true
  protected open fun createTabBorder(): JBTabsBorder {
    return JBDefaultTabsBorder(this)
  }

  protected open fun createTabPainterAdapter(): TabPainterAdapter {
    return DefaultTabPainterAdapter(DEFAULT)
  }

  private var tabLabelAtMouse: TabLabel? = null
  private val myScrollBar: JBScrollBar
  private val myScrollBarChangeListener: ChangeListener
  private var myScrollBarOn = false

  constructor(project: Project) : this(project, project)
  private constructor(project: Project, parent: Disposable) : this(project, IdeFocusManager.getInstance(project), parent)

  init {
    myFocusManager = focusManager ?: IdeFocusManager.getGlobalInstance()
    isOpaque = true
    background = tabPainter.getBackgroundColor()
    border = myBorder
    myNavigationActions = DefaultActionGroup()
    myNextAction = SelectNextAction(this, parentDisposable)
    myPrevAction = SelectPreviousAction(this, parentDisposable)
    myNavigationActions.add(myNextAction)
    myNavigationActions.add(myPrevAction)
    setUiDecorator(null)
    mySingleRowLayout = createSingleRowLayout()
    setLayout(mySingleRowLayout)
    mySplitter.divider.isOpaque = false
    myPopupListener = object : PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}
      override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
        disposePopupListener()
      }

      override fun popupMenuCanceled(e: PopupMenuEvent) {
        disposePopupListener()
      }
    }
    val actionManager = ActionManager.getInstance()
    myMoreToolbar = createToolbar(DefaultActionGroup(actionManager.getAction("TabList")))
    add(myMoreToolbar.component)
    val entryPointActionGroup = entryPointActionGroup
    if (entryPointActionGroup != null) {
      myEntryPointToolbar = createToolbar(entryPointActionGroup)
      add(myEntryPointToolbar!!.component)
    }
    else {
      myEntryPointToolbar = null
    }
    add(myTitleWrapper)
    Disposer.register(parentDisposable) { setTitleProducer(null) }

    // This scroll pane won't be shown on screen, it is needed only to handle scrolling events and properly update scrolling model
    val fakeScrollPane: JScrollPane = JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                   ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS)
    myScrollBar = JBThinOverlappingScrollBar(if (isHorizontalTabs) Adjustable.HORIZONTAL else Adjustable.VERTICAL)
    fakeScrollPane.verticalScrollBar = myScrollBar
    fakeScrollPane.horizontalScrollBar = myScrollBar
    fakeScrollPane.isVisible = true
    fakeScrollPane.setBounds(0, 0, 0, 0)
    add(myScrollBar)
    addMouseWheelListener { event: MouseWheelEvent ->
      val modifiers = UIUtil.getAllModifiers(event) or if (isHorizontalTabs) InputEvent.SHIFT_DOWN_MASK else 0
      val e = MouseEventAdapter.convert(event, fakeScrollPane, event.id, event.getWhen(),
                                        modifiers, event.x, event.y)
      MouseEventAdapter.redispatch(e, fakeScrollPane)
    }
    val listener: AWTEventListener = object : AWTEventListener {
      val afterScroll = Alarm(parentDisposable)
      override fun eventDispatched(event: AWTEvent) {
        var tabRectangle: Rectangle? = null
        if (mySingleRowLayout.myLastSingRowLayout != null) {
          tabRectangle = mySingleRowLayout.myLastSingRowLayout.tabRectangle
        }
        else if (myTableLayout.myLastTableLayout != null) {
          tabRectangle = myTableLayout.myLastTableLayout.tabRectangle
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
      val toolkit = Toolkit.getDefaultToolkit()
      toolkit?.removeAWTEventListener(listener)
    }
    myAnimator = object : Animator("JBTabs Attractions", 2, 500, true) {
      override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
        repaintAttractions()
      }
    }
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
      override fun getDefaultComponent(aContainer: Container): Component {
        return toFocus
      }
    }
    val listener1: LazyUiDisposable<JBTabsImpl> = object : LazyUiDisposable<JBTabsImpl?>(parentDisposable, this, this) {
      override fun initialize(parent: Disposable, child: JBTabsImpl, project: Project?) {
        if (myProject == null && project != null) {
          myProject = project
        }
        Disposer.register(parentDisposable, myAnimator)
        Disposer.register(parentDisposable) { removeTimerUpdate() }
        val gp = IdeGlassPaneUtil.find(child)
        myTabActionsAutoHideListenerDisposable = Disposer.newDisposable("myTabActionsAutoHideListener")
        Disposer.register(parentDisposable, myTabActionsAutoHideListenerDisposable)
        gp.addMouseMotionPreprocessor(myTabActionsAutoHideListener, myTabActionsAutoHideListenerDisposable)
        myGlassPane = gp
        addAwtListener({ __: AWTEvent? ->
                         if (!JBPopupFactory.getInstance().getChildPopups(this@JBTabsImpl).isEmpty()) return@addAwtListener
                         processFocusChange()
                       }, AWTEvent.FOCUS_EVENT_MASK, parentDisposable)
        dragHelper = createDragHelper(child, parentDisposable)
        dragHelper!!.start()
        if (myProject != null && myFocusManager === IdeFocusManager.getGlobalInstance()) {
          myFocusManager = IdeFocusManager.getInstance(myProject)
        }
      }
    }
    listener1.setupListeners()
    ClientProperty.put(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, Iterable {
      JBIterable.from(
        visibleInfos)
        .filter(Conditions.not(Conditions.`is`(mySelectedInfo)))
        .transform { info: TabInfo? -> info!!.component }.iterator()
    } as Iterable<Component?>)
    val hoverListener: HoverListener = object : HoverListener() {
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
    var area = myHeaderFitSize
    if (myTableLayout.myLastTableLayout != null) {
      area = myTableLayout.myLastTableLayout.tabRectangle.size
    }
    return if (area == null) false
    else when (tabsPosition) {
      JBTabsPosition.top -> y <= area.height
      JBTabsPosition.left -> x <= area.width
      JBTabsPosition.bottom -> y >= height - area.height
      JBTabsPosition.right -> x >= width - area.width
    }
  }

  private fun toggleScrollBar(isOn: Boolean) {
    if (isOn == myScrollBarOn) return
    myScrollBarOn = isOn
    myScrollBar.toggle(isOn)
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
    protected get() = null
  private val scrollBarBounds: Rectangle
    private get() = if (!isWithScrollBar || isHideTabs) Rectangle(0, 0, 0, 0)
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
    private get() = myScrollBar.model
  val isWithScrollBar: Boolean
    get() = effectiveLayout!!.isWithScrollBar

  protected open fun createDragHelper(tabs: JBTabsImpl, parentDisposable: Disposable): DragHelper {
    return DragHelper(tabs, parentDisposable)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    for ((info, label) in myInfo2Label) {
      info!!.revalidate()
      label.setTabActions(info.tabLabelActions)
    }
    updateRowLayout()
  }

  private fun updateRowLayout() {
    if (tabsPosition != JBTabsPosition.top) {
      mySingleRow = true
    }
    val forceTableLayout = (tabsPosition == JBTabsPosition.top && supportsTableLayoutAsSingleRow()
                            && TabLayout.showPinnedTabsSeparately())
    val useTableLayout = !isSingleRow || forceTableLayout
    if (useTableLayout) {
      myTableLayout = createTableLayout()
    }
    else {
      mySingleRowLayout = createSingleRowLayout()
    }
    val layout = if (useTableLayout) myTableLayout else mySingleRowLayout
    layout.scroll(scrollBarModel.value) // set current scroll value to new layout
    setLayout(layout)
    relayout(true, true)
  }

  protected open fun supportsTableLayoutAsSingleRow(): Boolean {
    return false
  }

  protected fun createTableLayout(): TableLayout {
    val isWithScrollBar = ((ExperimentalUI.isEditorTabsWithScrollBar()
                            && isSingleRow && tabsPosition == JBTabsPosition.top) && supportsTableLayoutAsSingleRow()
                           && TabLayout.showPinnedTabsSeparately()
                           && (!supportsCompression() || getInstance().hideTabsIfNeeded))
    return TableLayout(this, isWithScrollBar)
  }

  protected open fun createSingleRowLayout(): SingleRowLayout {
    return ScrollableSingleRowLayout(this)
  }

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

  fun isHoveredTab(label: TabLabel?): Boolean {
    return label != null && label === tabLabelAtMouse
  }

  open fun isActiveTabs(info: TabInfo?): Boolean {
    return UIUtil.isFocusAncestor(this)
  }

  override fun isEditorTabs(): Boolean {
    return false
  }

  fun supportsCompression(): Boolean {
    return mySupportsCompression
  }

  override fun setNavigationActionsEnabled(enabled: Boolean): JBTabs {
    myNavigationActionsEnabled = enabled
    return this
  }

  fun addNestedTabs(tabs: JBTabsImpl, parentDisposable: Disposable) {
    myNestedTabs.add(tabs)
    Disposer.register(parentDisposable) { myNestedTabs.remove(tabs) }
  }

  fun isDragOut(label: TabLabel?, deltaX: Int, deltaY: Int): Boolean {
    return effectiveLayout!!.isDragOut(label!!, deltaX, deltaY)
  }

  fun ignoreTabLabelLimitedWidthWhenPaint(): Boolean {
    return effectiveLayout is ScrollableSingleRowLayout || effectiveLayout is TableLayout && TabLayout.showPinnedTabsSeparately()
  }

  fun resetTabsCache() {
    EDT.assertIsEdt()
    myAllTabs = null
  }

  private fun processFocusChange() {
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    if (owner == null) {
      setFocused(false)
      return
    }
    if (owner === this || SwingUtilities.isDescendingFrom(owner, this)) {
      setFocused(true)
    }
    else {
      setFocused(false)
    }
  }

  private fun repaintAttractions() {
    var needsUpdate = false
    for (each in myVisibleInfos) {
      val eachLabel = myInfo2Label[each]
      needsUpdate = needsUpdate or eachLabel!!.repaintAttraction()
    }
    if (needsUpdate) {
      relayout(true, false)
    }
  }

  override fun addNotify() {
    super.addNotify()
    addTimerUpdate()
    scrollBarModel.addChangeListener(myScrollBarChangeListener)
    if (myDeferredFocusRequest != null) {
      val request: Runnable = myDeferredFocusRequest
      myDeferredFocusRequest = null
      request.run()
    }
  }

  override fun remove(index: Int) {
    if (myRemoveNotifyInProgress) {
      LOG.warn(IllegalStateException("removeNotify in progress"))
    }
    super.remove(index)
  }

  override fun removeAll() {
    if (myRemoveNotifyInProgress) {
      LOG.warn(IllegalStateException("removeNotify in progress"))
    }
    super.removeAll()
  }

  override fun removeNotify() {
    try {
      myRemoveNotifyInProgress = true
      super.removeNotify()
    }
    finally {
      myRemoveNotifyInProgress = false
    }
    setFocused(false)
    removeTimerUpdate()
    scrollBarModel.removeChangeListener(myScrollBarChangeListener)
    if (ScreenUtil.isStandardAddRemoveNotify(this) && myGlassPane != null) {
      Disposer.dispose(myTabActionsAutoHideListenerDisposable)
      myTabActionsAutoHideListenerDisposable = Disposer.newDisposable()
      myGlassPane = null
    }
  }

  public override fun processMouseEvent(e: MouseEvent) {
    super.processMouseEvent(e)
  }

  private fun addTimerUpdate() {
    if (!myListenerAdded) {
      ActionManager.getInstance().addTimerListener(this)
      myListenerAdded = true
    }
  }

  private fun removeTimerUpdate() {
    if (myListenerAdded) {
      ActionManager.getInstance().removeTimerListener(this)
      myListenerAdded = false
    }
  }

  fun layoutComp(data: SingleRowPassInfo, deltaX: Int, deltaY: Int, deltaWidth: Int, deltaHeight: Int) {
    val hToolbar = data.hToolbar.get()
    val vToolbar = data.vToolbar.get()
    if (hToolbar != null) {
      val toolbarHeight = hToolbar.preferredSize.height
      val compRect = layoutComp(deltaX, toolbarHeight + deltaY, data.comp.get(), deltaWidth, deltaHeight)
      layout(hToolbar, compRect.x, compRect.y - toolbarHeight, compRect.width, toolbarHeight)
    }
    else if (vToolbar != null) {
      val toolbarWidth = vToolbar.preferredSize.width
      val vSeparatorWidth = if (toolbarWidth > 0) 1 else 0
      if (isSideComponentBefore) {
        val compRect = layoutComp(toolbarWidth + vSeparatorWidth + deltaX, deltaY, data.comp.get(), deltaWidth, deltaHeight)
        layout(vToolbar, compRect.x - toolbarWidth - vSeparatorWidth, compRect.y, toolbarWidth, compRect.height)
      }
      else {
        val compRect = layoutComp(Rectangle(deltaX, deltaY, width - toolbarWidth - vSeparatorWidth, height),
                                  data.comp.get(), deltaWidth, deltaHeight)
        layout(vToolbar, compRect.x + compRect.width + vSeparatorWidth, compRect.y, toolbarWidth, compRect.height)
      }
    }
    else {
      layoutComp(deltaX, deltaY, data.comp.get(), deltaWidth, deltaHeight)
    }
  }

  fun isDropTarget(info: TabInfo): Boolean {
    return myDropInfo != null && myDropInfo == info
  }

  private fun setDropInfoIndex(dropInfoIndex: Int) {
    myDropInfoIndex = dropInfoIndex
  }

  @MagicConstant(
    intValues = [SwingConstants.CENTER.toLong(), SwingConstants.TOP.toLong(), SwingConstants.LEFT.toLong(), SwingConstants.BOTTOM.toLong(), SwingConstants.RIGHT.toLong(), -1])
  private fun setDropSide(side: Int) {
    myDropSide = side
  }

  fun getFirstTabOffset(): Int {
    return myFirstTabOffset
  }

  override fun setFirstTabOffset(firstTabOffset: Int) {
    myFirstTabOffset = firstTabOffset
  }

  override fun setEmptyText(text: String?): JBTabsPresentation {
    myEmptyText = text
    return this
  }

  /**
   * TODO use RdGraphicsExKt#childAtMouse(IdeGlassPane, Container)
   */
  @Deprecated("")
  internal inner class TabActionsAutoHideListener : MouseMotionAdapter(), Weighted {
    private var myCurrentOverLabel: TabLabel? = null
    private var myLastOverPoint: Point? = null
    override fun getWeight(): Double {
      return 1
    }

    override fun mouseMoved(e: MouseEvent) {
      if (!myTabLabelActionsAutoHide) return
      myLastOverPoint = SwingUtilities.convertPoint(e.component, e.x, e.y, this@JBTabsImpl)
      processMouseOver()
    }

    fun processMouseOver() {
      if (!myTabLabelActionsAutoHide || myLastOverPoint == null) {
        return
      }
      if (myLastOverPoint!!.x >= 0 && myLastOverPoint!!.x < width && myLastOverPoint!!.y > 0 && myLastOverPoint!!.y < height) {
        val label = myInfo2Label[_findInfo(myLastOverPoint!!, true)]
        if (label != null) {
          if (myCurrentOverLabel != null) {
            myCurrentOverLabel!!.toggleShowActions(false)
          }
          label.toggleShowActions(true)
          myCurrentOverLabel = label
          return
        }
      }
      if (myCurrentOverLabel != null) {
        myCurrentOverLabel!!.toggleShowActions(false)
        myCurrentOverLabel = null
      }
    }
  }

  override fun getModalityState(): ModalityState {
    return ModalityState.stateForComponent(this)
  }

  override fun run() {
    updateTabActions(false)
  }

  override fun updateTabActions(validateNow: Boolean) {
    if (isHideTabs) return
    val changed = Ref(java.lang.Boolean.FALSE)
    for (eachInfo in myInfo2Label.keys) {
      val changes = myInfo2Label[eachInfo]!!.updateTabActions()
      changed.set(changed.get() || changes)
    }
    if (changed.get()) {
      revalidateAndRepaint()
    }
  }

  val entryPointPreferredSize: Dimension
    get() = if (myEntryPointToolbar == null) Dimension() else myEntryPointToolbar!!.component.preferredSize
  val moreToolbarPreferredSize: Dimension
    // Returns default one action horizontal toolbar size (26x24)
    get() {
      val baseSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      return Dimension(baseSize.width + JBUI.scale(4), baseSize.height + JBUI.scale(2))
    }
  private val entryPointRect: Rectangle?
    private get() {
      if (effectiveLayout is SingleRowLayout) {
        val lastLayout = mySingleRowLayout.myLastSingRowLayout
        return lastLayout?.entryPointRect
      }
      if (effectiveLayout is TableLayout) {
        val lastLayout = myTableLayout.myLastTableLayout
        return lastLayout?.entryPointRect
      }
      return null
    }
  private val moreRect: Rectangle?
    private get() {
      if (effectiveLayout is SingleRowLayout) {
        val lastLayout = mySingleRowLayout.myLastSingRowLayout
        return lastLayout?.moreRect
      }
      if (effectiveLayout is TableLayout) {
        val lastLayout = myTableLayout.myLastTableLayout
        return lastLayout?.moreRect
      }
      return null
    }
  private val titleRect: Rectangle?
    private get() {
      if (effectiveLayout is SingleRowLayout) {
        val lastLayout = mySingleRowLayout.myLastSingRowLayout
        return lastLayout?.titleRect
      }
      if (effectiveLayout is TableLayout) {
        val lastLayout = myTableLayout.myLastTableLayout
        return lastLayout?.titleRect
      }
      return null
    }

  override fun setTitleProducer(titleProducer: Producer<out Pair<Icon, String>>?) {
    myTitleWrapper.removeAll()
    if (titleProducer != null) {
      val toolbar = ActionManager.getInstance()
        .createActionToolbar(ActionPlaces.TABS_MORE_TOOLBAR, DefaultActionGroup(TitleAction(titleProducer)), true)
      toolbar.targetComponent = null
      toolbar.setMiniMode(true)
      myTitleWrapper.setContent(toolbar.component)
    }
  }

  override fun canShowMorePopup(): Boolean {
    val rect = moreRect
    return rect != null && !rect.isEmpty
  }

  override fun showMorePopup(): JBPopup? {
    val rect = moreRect ?: return null
    val hiddenInfos = ContainerUtil.filter(
      visibleInfos) { tabInfo: TabInfo? -> mySingleRowLayout.isTabHidden(tabInfo) }
    return if (ExperimentalUI.isNewUI()) {
      showListPopup(rect, hiddenInfos)
    }
    else {
      showTabLabelsPopup(rect, hiddenInfos)
    }
  }

  private fun showListPopup(rect: Rectangle, hiddenInfos: List<TabInfo?>): JBPopup {
    val separatorIndex = ContainerUtil.indexOf(hiddenInfos) { info: TabInfo? ->
      val label = myInfo2Label[info]
      if (position.isSide) label!!.y >= 0 else label!!.x >= 0
    }
    val separatorInfo = if (separatorIndex > 0) hiddenInfos[separatorIndex] else null
    val step = HiddenInfosListPopupStep(hiddenInfos, separatorInfo)
    val selectedIndex = ClientProperty.get(this, HIDDEN_INFOS_SELECT_INDEX_KEY)
    if (selectedIndex != null) {
      step.defaultOptionIndex = selectedIndex
    }
    val popup = JBPopupFactory.getInstance().createListPopup(myProject!!, step) { renderer: ListCellRenderer<*>? ->
      val descriptor: ListItemDescriptor<TabInfo> = object : ListItemDescriptorAdapter<TabInfo?>() {
        override fun getTextFor(value: TabInfo): String {
          return@createListPopup value.text
        }

        override fun getIconFor(value: TabInfo): Icon? {
          return@createListPopup value.icon
        }

        override fun hasSeparatorAboveOf(value: TabInfo): Boolean {
          return@createListPopup value == separatorInfo
        }
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
              if (ClientProperty.get<Boolean>(c, SELECTED_KEY) !== java.lang.Boolean.TRUE) {
                return@createListPopup
              }
              val inset = JBUI.scale(2)
              val arc = JBUI.scale(4)
              val theme: TabTheme = tabPainter.getTabTheme()
              val rect = Rectangle(x, y + inset, theme.underlineHeight, height - inset * 2)
              (g as Graphics2D).fill2DRoundRect(rect, arc.toDouble(), theme.underlineColor)
            }

            override fun getBorderInsets(c: Component): Insets {
              return@createListPopup JBInsets.create(Insets(0, 9, 0, 3))
            }

            override fun isBorderOpaque(): Boolean {
              return@createListPopup true
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
              return@createListPopup preferredSize
            }
          }
          textLabel.setMyBorder(null)
          textLabel.setIpad(JBInsets.emptyInsets())
          textLabel.setOpaque(true)
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
          return@createListPopup result
        }

        private fun addActionLabel() {
          actionLabel = JLabel()
          component!!.add(actionLabel)
        }

        protected override fun customizeComponent(list: JList<out TabInfo>, info: TabInfo, isSelected: Boolean) {
          if (actionLabel != null) {
            val isHovered = ClientProperty.get(list, HOVER_INDEX_KEY) == myCurrentIndex
            val icon = getTabActionIcon(info, isHovered)
            actionLabel!!.icon = icon
            ClientProperty.put(actionLabel!!, TAB_INFO_KEY, info)
            addMouseListener(list)
          }
          val selectedInfo = selectedInfo
          var icon = info.icon
          if (icon != null && info != selectedInfo) {
            icon = getTransparentIcon(icon, JBUI.CurrentTheme.EditorTabs.unselectedAlpha())
          }
          iconLabel!!.icon = icon
          textLabel!!.clear()
          info.coloredText.appendToComponent(textLabel!!)
          val customBackground = info.tabColor
          myRendererComponent.background = customBackground ?: JBUI.CurrentTheme.Popup.BACKGROUND
          ClientProperty.put(component!!, SELECTED_KEY, if (info == selectedInfo) true else null)
          component!!.invalidate()
        }

        override fun setComponentIcon(icon: Icon, disabledIcon: Icon) {
          // icon will be set in customizeComponent
        }

        override fun createSeparator(): SeparatorWithText {
          val labelInsets = JBUI.CurrentTheme.Popup.separatorLabelInsets()
          return@createListPopup GroupHeaderSeparator(labelInsets)
        }

        private fun addMouseListener(list: JList<out TabInfo>) {
          if (listMouseListener != null) return@createListPopup
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
                return@createListPopup
              }
              val tabInfo = ClientProperty.get(renderer, TAB_INFO_KEY)
              if (tabInfo == null) {
                return@createListPopup
              }

              // The last one is expected to be 'CloseTab'
              val tabAction = if (tabInfo.getTabLabelActions() != null) ArrayUtil.getLastElement(
                tabInfo.getTabLabelActions().getChildren(null))
              else null
              if (tabAction == null && !tabInfo.isPinned()) {
                return@createListPopup
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
          Arrays.stream(listeners).forEach { l: MouseListener? -> list.removeMouseListener(l) }
          Arrays.stream(motionListeners).forEach { l: MouseMotionListener? -> list.removeMouseMotionListener(l) }
          list.addMouseListener(listMouseListener)
          list.addMouseMotionListener(listMouseListener)
          Arrays.stream(listeners).forEach { l: MouseListener? -> list.addMouseListener(l) }
          Arrays.stream(motionListeners).forEach { l: MouseMotionListener? -> list.addMouseMotionListener(l) }
        }
      }
    }
    popup.content.putClientProperty(MorePopupAware::class.java, java.lang.Boolean.TRUE)
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

  private inner class HiddenInfosListPopupStep(values: List<TabInfo?>, private val separatorInfo: TabInfo?) : BaseListPopupStep<TabInfo?>(
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

    override fun getSeparatorAbove(value: TabInfo): ListSeparator? {
      return if (value == separatorInfo) ListSeparator() else null
    }

    override fun getIconFor(value: TabInfo): Icon? {
      return value.icon
    }

    override fun getTextFor(value: TabInfo): String {
      return value.text
    }
  }

  private fun showTabLabelsPopup(rect: Rectangle, hiddenInfos: List<TabInfo?>): JBPopup {
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
      label.apply(if (myUiDecorator != null) myUiDecorator!!.decoration else ourDefaultDecorator.decoration)
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
    popup.content.putClientProperty(MorePopupAware::class.java, java.lang.Boolean.TRUE)
    popup.show(RelativePoint(this, Point(rect.x, rect.y + rect.height)))
    return popup
  }

  private val toFocus: JComponent?
    private get() {
      val info = selectedInfo
      if (LOG.isDebugEnabled) {
        LOG.debug("selected info: $info")
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
        val policyToFocus = myFocusManager.getFocusTargetFor(toFocus)
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
    val toFocus = toFocus
    return toFocus?.requestFocusInWindow() ?: super.requestFocusInWindow()
  }

  override fun addTab(info: TabInfo, index: Int): TabInfo {
    return addTab(info, index, false, true)
  }

  override fun addTabSilently(info: TabInfo, index: Int): TabInfo {
    return addTab(info, index, false, false)
  }

  private fun addTab(info: TabInfo, index: Int, isDropTarget: Boolean, fireEvents: Boolean): TabInfo {
    if (!isDropTarget && tabs.contains(info)) {
      return tabs[tabs.indexOf(info)]
    }
    info.changeSupport.addPropertyChangeListener(this)
    val label = createTabLabel(info)
    myInfo2Label[info] = label
    myInfo2Page[info] = AccessibleTabPage(info)
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

  protected open fun createTabLabel(info: TabInfo?): TabLabel {
    return TabLabel(this, info)
  }

  override fun addTab(info: TabInfo): TabInfo {
    return addTab(info, -1)
  }

  override fun getTabLabel(info: TabInfo): TabLabel {
    return myInfo2Label[info]!!
  }

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
    myAddNavigationGroup = addNavigationGroup
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
    private get() {
      val owner = focusOwner ?: return false
      return if (mySelectedInfo != null && !SwingUtilities.isDescendingFrom(owner, mySelectedInfo!!.component)) {
        false
      }
      else SwingUtilities.isDescendingFrom(owner, this)
    }

  override fun select(info: TabInfo, requestFocus: Boolean): ActionCallback {
    return _setSelected(info, requestFocus, false)
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
      val owner = myFocusManager.focusOwner
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
    val label = myInfo2Label[info]
    if (label != null) {
      setComponentZOrder(label, 0)
    }
    setComponentZOrder(myScrollBar, 0)
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
        if (myProject.isDisposed) {
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
                                                          myFocusManager.requestFocusInProject(this,
                                                                                               myProject)
                                                        }
                                                      }, ModalityState.NON_MODAL)
      removeDeferred()
    }
  }

  protected open val focusOwnerToStore: JComponent?
    protected get() {
      val owner = focusOwner ?: return null
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
          myFocusManager.requestFocusInProject(toFocus, myProject).notifyWhenDone(res)
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
    myFocusManager.doWhenFocusSettlesDown {
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
      val eachLabel = myInfo2Label[tabInfo]
      eachLabel!!.setTabEnabled(tabInfo.isEnabled)
    }
    val selected = selectedInfo
    if (selected != null && !selected.isEnabled) {
      val toSelect = getToSelectOnRemoveOf(selected)
      if (toSelect != null) {
        select(toSelect, myFocusManager.getFocusedDescendantFor(this) != null)
      }
    }
  }

  private fun updateHiding() {
    var update = false
    val visible = myVisibleInfos.iterator()
    while (visible.hasNext()) {
      val each = visible.next()
      if (each.isHidden && !myHiddenInfos.containsKey(each)) {
        myHiddenInfos[each] = myVisibleInfos.indexOf(each)
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
    myInfo2Label[tabInfo]!!.setIcon(tabInfo.icon)
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
      myAttractions.add(tabInfo)
    }
    else {
      myAttractions.remove(tabInfo)
      tabInfo.blinkCount = 0
    }
    if (start && !myAnimator.isRunning) {
      myAnimator.resume()
    }
    else if (!start && myAttractions.isEmpty()) {
      myAnimator.suspend()
      repaintAttractions()
    }
  }

  private fun updateText(tabInfo: TabInfo) {
    val label = myInfo2Label[tabInfo]
    label!!.setText(tabInfo.coloredText)
    label.toolTipText = tabInfo.tooltipText
  }

  private fun updateSideComponent(tabInfo: TabInfo) {
    val old = myInfo2Toolbar[tabInfo]
    old?.let { remove(it) }
    val toolbar = createToolbarComponent(tabInfo)
    myInfo2Toolbar[tabInfo] = toolbar
    add(toolbar)
  }

  private fun updateTabActions(info: TabInfo) {
    myInfo2Label[info]!!.setTabActions(info.tabLabelActions)
  }

  override fun getSelectedInfo(): TabInfo? {
    if (myOldSelection != null) {
      return myOldSelection
    }
    return if (mySelectedInfo == null) {
      if (myVisibleInfos.isEmpty()) null else myVisibleInfos[0]
    }
    else if (myVisibleInfos.contains(mySelectedInfo)) {
      mySelectedInfo
    }
    else {
      setSelectedInfo(null)
      null
    }
  }

  private fun setSelectedInfo(info: TabInfo?) {
    mySelectedInfo = info
    myInfo2Toolbar.forEach { (tabInfo: TabInfo?, toolbar: Toolbar) -> toolbar.isVisible = info == tabInfo }
  }

  override fun getToSelectOnRemoveOf(info: TabInfo): TabInfo? {
    if (!myVisibleInfos.contains(info)) return null
    if (mySelectedInfo != info) return null
    if (myVisibleInfos.size == 1) return null
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
    if (from < 0) return null
    var index = from
    val infos = visibleInfos
    while (true) {
      index++
      if (index == infos.size) {
        if (!cycle) break
        index = 0
      }
      if (index == from) break
      val each = infos[index]
      if (each!!.isEnabled) return each
    }
    return null
  }

  fun findEnabledBackward(from: Int, cycle: Boolean): TabInfo? {
    if (from < 0) return null
    var index = from
    val infos = visibleInfos
    while (true) {
      index--
      if (index == -1) {
        if (!cycle) break
        index = infos.size - 1
      }
      if (index == from) break
      val each = infos[index]
      if (each!!.isEnabled) return each
    }
    return null
  }

  private fun createToolbarComponent(tabInfo: TabInfo): Toolbar {
    return Toolbar(this, tabInfo)
  }

  override fun getTabAt(tabIndex: Int): TabInfo {
    return tabs[tabIndex]
  }

  override fun getTabs(): List<TabInfo> {
    EDT.assertIsEdt()
    if (myAllTabs != null) {
      return myAllTabs
    }
    val result: List<TabInfo> = ArrayList(myVisibleInfos)
    for (each in myHiddenInfos.keys) {
      result.add(getIndexInVisibleArray(each), each)
    }
    if (isAlphabeticalMode) {
      sortTabsAlphabetically(result)
    }
    myAllTabs = result
    return result
  }

  override fun getTargetInfo(): TabInfo? {
    return if (myPopupInfo != null) myPopupInfo else selectedInfo
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
      if (myActivePopup == null) {
        myPopupInfo = null
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
          if (place != null && place != ActionPlaces.UNKNOWN) place else "JBTabs", group, tabs.myHorizontalSide)
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
      val label = tabs.myInfo2Label[info]
      return if (tabs.myHorizontalSide && tabs.isSideComponentOnTabs && label != null && base.height > 0) {
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
      if (!ExperimentalUI.isNewUI() && myEntryPointToolbar != null && myEntryPointToolbar!!.component.isVisible) {
        extent = myEntryPointToolbar!!.component.y
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
      val labels: Collection<TabLabel> = myInfo2Label.values
      for (each in labels) {
        each.setTabActionsAutoHide(myTabLabelActionsAutoHide)
      }
      val moreBoundsBeforeLayout = myMoreToolbar!!.component.bounds
      val entryPointBoundsBeforeLayout = if (myEntryPointToolbar != null) myEntryPointToolbar!!.component.bounds else Rectangle(0, 0, 0, 0)
      myHeaderFitSize = computeHeaderFitSize()
      val visible: MutableList<TabInfo?> = ArrayList(
        visibleInfos)
      if (myDropInfo != null && !visible.contains(myDropInfo) && myShowDropLocation) {
        if (dropInfoIndex >= 0 && dropInfoIndex < visible.size) {
          visible.add(dropInfoIndex, myDropInfo)
        }
        else {
          visible.add(myDropInfo)
        }
      }
      if (effectiveLayout is SingleRowLayout) {
        mySingleRowLayout.scrollSelectionInView()
        lastLayoutPass = mySingleRowLayout.layoutSingleRow(visible)
        val titleRect = titleRect
        if (titleRect != null && !titleRect.isEmpty) {
          val preferredSize = myTitleWrapper.preferredSize
          val bounds = Rectangle(titleRect)
          JBInsets.removeFrom(bounds, layoutInsets)
          val xDiff = (bounds.width - preferredSize.width) / 2
          val yDiff = (bounds.height - preferredSize.height) / 2
          bounds.x += xDiff
          bounds.width -= 2 * xDiff
          bounds.y += yDiff
          bounds.height -= 2 * yDiff
          myTitleWrapper.bounds = bounds
        }
        else {
          myTitleWrapper.bounds = Rectangle()
        }
        myTableLayout.myLastTableLayout = null
        val divider = mySplitter.divider
        if (divider.parent === this) {
          val location = if (tabsPosition == JBTabsPosition.left) mySingleRowLayout.myLastSingRowLayout.tabRectangle.width else width - mySingleRowLayout.myLastSingRowLayout.tabRectangle.width
          divider.setBounds(location, 0, 1, height)
        }
      }
      else {
        myTableLayout.scrollSelectionInView()
        lastLayoutPass = myTableLayout.layoutTable(visible)
        mySingleRowLayout.myLastSingRowLayout = null
      }
      centerizeEntryPointToolbarPosition()
      centerizeMoreToolbarPosition()
      moveDraggedTabLabel()
      myTabActionsAutoHideListener.processMouseOver()
      applyResetComponents()
      myScrollBar.orientation = if (isHorizontalTabs) Adjustable.HORIZONTAL else Adjustable.VERTICAL
      myScrollBar.bounds = scrollBarBounds
      updateScrollBarModel()
      updateToolbarIfVisibilityChanged(myMoreToolbar, moreBoundsBeforeLayout)
      updateToolbarIfVisibilityChanged(myEntryPointToolbar, entryPointBoundsBeforeLayout)
    }
    finally {
      myForcedRelayout = false
    }
  }

  private val tabsAreaWidth: Int
    private get() {
      if (myMoreToolbar!!.component.isVisible) {
        return myMoreToolbar.component.x
      }
      else if (myEntryPointToolbar != null && myEntryPointToolbar!!.component.isVisible && myTableLayout.myLastTableLayout == null) {
        return myEntryPointToolbar!!.component.x
      }
      return bounds.width
    }

  private fun centerizeMoreToolbarPosition() {
    val moreRect = moreRect
    val mComponent = myMoreToolbar!!.component
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
    val eComponent = (if (myEntryPointToolbar == null) null else myEntryPointToolbar!!.component) ?: return
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
    if (dragHelper != null && dragHelper.myDragRec != null) {
      val selectedLabel = myInfo2Label[draggedTabSelectionInfo]
      if (selectedLabel != null) {
        val bounds = selectedLabel.bounds
        if (isHorizontalTabs) {
          selectedLabel.setBounds(dragHelper.myDragRec.x, bounds.y, bounds.width, bounds.height)
        }
        else {
          selectedLabel.setBounds(bounds.x, dragHelper.myDragRec.y, bounds.width, bounds.height)
        }
      }
    }
  }

  protected open val draggedTabSelectionInfo: TabInfo?
    protected get() = selectedInfo

  private fun computeHeaderFitSize(): Dimension {
    val max = computeMaxSize()
    return if (position == JBTabsPosition.top || position == JBTabsPosition.bottom) {
      Dimension(size.width,
                if (myHorizontalSide) Math.max(max.myLabel.height, max.myToolbar.height) else max.myLabel.height)
    }
    else Dimension(
      max.myLabel.width + if (myHorizontalSide) 0 else max.myToolbar.width, size.height)
  }

  fun layoutComp(componentX: Int, componentY: Int, comp: JComponent?, deltaWidth: Int, deltaHeight: Int): Rectangle {
    return layoutComp(Rectangle(componentX, componentY, width, height), comp, deltaWidth, deltaHeight)
  }

  fun layoutComp(bounds: Rectangle, comp: JComponent?, deltaWidth: Int, deltaHeight: Int): Rectangle {
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
    return layout(comp, x, y, width, height)
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
    if (myDropInfo != null) {
      reset(myDropInfo!!, resetLabels)
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
    resetLayout(myInfo2Toolbar[each])
    if (resetLabels) {
      resetLayout(myInfo2Label[each])
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (myVisibleInfos.isEmpty()) {
      if (myEmptyText != null) {
        setupAntialiasing(g)
        UIUtil.drawCenteredString((g as Graphics2D), Rectangle(0, 0, width, height), myEmptyText!!)
      }
      return
    }
    tabPainter.fillBackground((g as Graphics2D), Rectangle(0, 0, width, height))
    drawBorder(g)
    drawToolbarSeparator(g)
  }

  private fun drawToolbarSeparator(g: Graphics) {
    val toolbar = myInfo2Toolbar[selectedInfo]
    if (toolbar != null && toolbar.parent === this && !isSideComponentOnTabs && !myHorizontalSide && isHideTabs) {
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
    get() = myInfo2Label[selectedInfo]
  open val visibleInfos: List<TabInfo?>
    get() {
      if (getBoolean("editor.keep.pinned.tabs.on.left")) {
        groupPinnedFirst(myVisibleInfos)
      }
      if (isAlphabeticalMode) {
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
    private get() = myVisibleInfos.size > 1

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
      val label = myInfo2Label[eachInfo]
      max.myLabel.height = Math.max(max.myLabel.height, label!!.preferredSize.height)
      max.myLabel.width = Math.max(max.myLabel.width, label.preferredSize.width)
      if (isSideComponentOnTabs) {
        val toolbar = myInfo2Toolbar[eachInfo]
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
    val infos: Iterator<TabInfo?> = myInfo2Label.keys.iterator()
    val size = Dimension()
    var currentTab = 0
    val horizontal = tabsPosition == JBTabsPosition.top || tabsPosition == JBTabsPosition.bottom
    while (infos.hasNext()) {
      val canGrow = currentTab < tabsCount
      val eachInfo = infos.next()
      val eachLabel = myInfo2Label[eachInfo]
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
    if (myRemoveNotifyInProgress) {
      LOG.warn(IllegalStateException("removeNotify in progress"))
    }
    if (myPopupInfo == info) myPopupInfo = null
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
    val label = myInfo2Label[info]
    val toolbar = myInfo2Toolbar[info]
    val component = info.component
    val ancestorChecker = Predicate<Component> { focusOwner: Component? ->
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
    val tabLabel = myInfo2Label[info]
    tabLabel?.let { remove(it) }
    val toolbar = myInfo2Toolbar[info]
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
    myInfo2Label.remove(info)
    myInfo2Page.remove(info)
    myInfo2Toolbar.remove(info)
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
    if (effectiveLayout === mySingleRowLayout) {
      mySingleRowLayout.scrollSelectionInView()
    }
    else if (effectiveLayout === myTableLayout) {
      myTableLayout.scrollSelectionInView()
    }
    relayout(forced, layoutNow)
  }

  private fun updateEntryPointToolbar() {
    if (myEntryPointToolbar != null) {
      remove(myEntryPointToolbar!!.component)
    }
    val selectedInfo = selectedInfo
    val tabActionGroup = selectedInfo?.tabPaneActions
    val entryPointActionGroup = entryPointActionGroup
    if (tabActionGroup != null || entryPointActionGroup != null) {
      val group: ActionGroup
      group = if (tabActionGroup != null && entryPointActionGroup != null) {
        if ( // check that more toolbar and entry point toolbar are in the same row
          !myMoreToolbar!!.component.bounds.isEmpty && tabActionGroup.getChildren(
            null).size != 0 && (!TabLayout.showPinnedTabsSeparately() || ContainerUtil.all(
            tabs) { info: TabInfo -> !info.isPinned })) {
          DefaultActionGroup(FakeEmptyAction(), Separator.create(), tabActionGroup, Separator.create(), entryPointActionGroup)
        }
        else {
          DefaultActionGroup(tabActionGroup, Separator.create(), entryPointActionGroup)
        }
      }
      else {
        tabActionGroup ?: entryPointActionGroup!!
      }
      if (myEntryPointToolbar != null && myEntryPointToolbar!!.actionGroup == group) {
        // keep old toolbar to avoid blinking (actions need to be updated to be visible)
        add(myEntryPointToolbar!!.component)
      }
      else {
        myEntryPointToolbar = createToolbar(group)
        add(myEntryPointToolbar!!.component)
      }
    }
    else {
      myEntryPointToolbar = null
    }
  }

  val actionsInsets: Insets
    /**
     * @return insets, that should be used to layout [JBTabsImpl.myMoreToolbar] and [JBTabsImpl.myEntryPointToolbar]
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
      comp.apply(if (myUiDecorator != null) myUiDecorator!!.decoration else ourDefaultDecorator.decoration)
    }
    super.addImpl(comp, constraints, index)
  }

  fun relayout(forced: Boolean, layoutNow: Boolean) {
    if (!myForcedRelayout) {
      myForcedRelayout = forced
    }
    if (myMoreToolbar != null) {
      myMoreToolbar.component.isVisible = !isHideTabs &&
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
      val label = myInfo2Label[eachInfo]
      for (eachListener in myTabMouseListeners) {
        if (eachListener is MouseListener) {
          label!!.addMouseListener(eachListener)
        }
        else if (eachListener is MouseMotionListener) {
          label!!.addMouseMotionListener(eachListener)
        }
        else {
          assert(false)
        }
      }
    }
  }

  private fun removeListeners() {
    for (eachInfo in myVisibleInfos) {
      val label = myInfo2Label[eachInfo]
      for (eachListener in myTabMouseListeners) {
        if (eachListener is MouseListener) {
          label!!.removeMouseListener(eachListener)
        }
        else if (eachListener is MouseMotionListener) {
          label!!.removeMouseMotionListener(eachListener)
        }
        else {
          assert(false)
        }
      }
    }
  }

  private fun updateListeners() {
    removeListeners()
    addListeners()
  }

  override fun addListener(listener: TabsListener): JBTabs {
    return addListener(listener, null)
  }

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
    if (myEntryPointToolbar != null) {
      myEntryPointToolbar!!.component.isVisible = !myHideTabs
    }
    relayout(true, false)
  }

  /**
   * @param hideTopPanel true if tabs and top toolbar should be hidden from a view
   */
  override fun setHideTopPanel(hideTopPanel: Boolean) {
    if (isHideTopPanel == hideTopPanel) return
    myHideTopPanel = hideTopPanel
    tabs.stream()
      .map { obj: TabInfo -> obj.sideComponent }
      .forEach { component: JComponent -> component.isVisible = !myHideTopPanel }
    relayout(true, true)
  }

  override fun isHideTopPanel(): Boolean {
    return myHideTopPanel
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

  private abstract class BaseNavigationAction internal constructor(copyFromId: @NlsSafe String,
                                                                   private val myTabs: JBTabsImpl,
                                                                   parentDisposable: Disposable) : DumbAwareAction() {
    private val myShadow: ShadowAction

    init {
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
        _update(e, tabs, tabs.visibleInfos.indexOf(tabs.selectedInfo))
      }
    }

    fun findNavigatableTabs(tabs: JBTabsImpl?): JBTabsImpl? {
      // The debugger UI contains multiple nested JBTabsImpl, where the innermost JBTabsImpl has only one tab. In this case,
      // the action should target the outer JBTabsImpl.
      if (tabs == null || tabs !== myTabs) {
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
      tabs = findNavigatableTabs(tabs)
      if (tabs == null) {
        return
      }
      var infos: List<TabInfo?>
      var index: Int
      while (true) {
        infos = tabs!!.visibleInfos
        index = infos.indexOf(tabs.selectedInfo)
        if (index == -1) return
        tabs = if (borderIndex(infos, index) && tabs.navigatableParent() != null) {
          tabs.navigatableParent()
        }
        else {
          break
        }
      }
      _actionPerformed(e, tabs, index)
    }

    protected abstract fun borderIndex(infos: List<TabInfo?>, index: Int): Boolean
    protected abstract fun _actionPerformed(e: AnActionEvent?, tabs: JBTabsImpl?, selectedIndex: Int)
  }

  private class SelectNextAction(tabs: JBTabsImpl, parentDisposable: Disposable) : BaseNavigationAction(IdeActions.ACTION_NEXT_TAB, tabs,
                                                                                                        parentDisposable) {
    override fun _update(e: AnActionEvent, tabs: JBTabsImpl, selectedIndex: Int) {
      e.presentation.isEnabled = tabs.findEnabledForward(selectedIndex, true) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun borderIndex(infos: List<TabInfo?>, index: Int): Boolean {
      return index == infos.size - 1
    }

    override fun _actionPerformed(e: AnActionEvent?, tabs: JBTabsImpl?, selectedIndex: Int) {
      val tabInfo = tabs!!.findEnabledForward(selectedIndex, true)
      if (tabInfo != null) {
        val lastFocus = tabInfo.lastFocusOwner
        tabs.select(tabInfo, true)
        tabs.myNestedTabs.stream()
          .filter { nestedTabs: JBTabsImpl? -> lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs) }
          .forEach { nestedTabs: JBTabsImpl -> nestedTabs.selectFirstVisible() }
      }
    }
  }

  protected val isNavigatable: Boolean
    protected get() {
      val selectedIndex = visibleInfos.indexOf(selectedInfo)
      return isNavigationVisible && selectedIndex >= 0 && myNavigationActionsEnabled
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
    if (!isNavigatable) return
    val select = visibleInfos[0]!!
    val lastFocus = select.lastFocusOwner
    select(select, true)
    myNestedTabs.stream()
      .filter { nestedTabs: JBTabsImpl? -> lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs) }
      .forEach { nestedTabs: JBTabsImpl -> nestedTabs.selectFirstVisible() }
  }

  private fun selectLastVisible() {
    if (!isNavigatable) return
    val last = visibleInfos.size - 1
    val select = visibleInfos[last]!!
    val lastFocus = select.lastFocusOwner
    select(select, true)
    myNestedTabs.stream()
      .filter { nestedTabs: JBTabsImpl? -> lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs) }
      .forEach { nestedTabs: JBTabsImpl -> nestedTabs.selectLastVisible() }
  }

  private class SelectPreviousAction(tabs: JBTabsImpl, parentDisposable: Disposable) : BaseNavigationAction(IdeActions.ACTION_PREVIOUS_TAB,
                                                                                                            tabs, parentDisposable) {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun _update(e: AnActionEvent, tabs: JBTabsImpl, selectedIndex: Int) {
      e.presentation.isEnabled = tabs.findEnabledBackward(selectedIndex, true) != null
    }

    override fun borderIndex(infos: List<TabInfo?>, index: Int): Boolean {
      return index == 0
    }

    override fun _actionPerformed(e: AnActionEvent?, tabs: JBTabsImpl?, selectedIndex: Int) {
      val tabInfo = tabs!!.findEnabledBackward(selectedIndex, true)
      if (tabInfo != null) {
        val lastFocus = tabInfo.lastFocusOwner
        tabs.select(tabInfo, true)
        tabs.myNestedTabs.stream()
          .filter { nestedTabs: JBTabsImpl? -> lastFocus == null || SwingUtilities.isDescendingFrom(lastFocus, nestedTabs) }
          .forEach { nestedTabs: JBTabsImpl -> nestedTabs.selectLastVisible() }
      }
    }
  }

  private fun disposePopupListener() {
    if (myActivePopup != null) {
      myActivePopup!!.removePopupMenuListener(myPopupListener)
      myActivePopup = null
    }
  }

  override fun setSideComponentVertical(vertical: Boolean): JBTabsPresentation {
    myHorizontalSide = !vertical
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
    mySingleRow = singleRow
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
    return mySingleRow
  }

  val isSideComponentVertical: Boolean
    get() = !myHorizontalSide

  override fun setUiDecorator(decorator: UiDecorator?): JBTabsPresentation {
    myUiDecorator = decorator ?: ourDefaultDecorator
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
    if (myUiDecorator != null) {
      val uiDecoration = myUiDecorator!!.decoration
      for (each in myInfo2Label.values) {
        each.apply(uiDecoration)
      }
    }
    for (each in tabs) {
      adjust(each)
    }
    relayout(true, false)
  }

  protected open fun adjust(each: TabInfo) {
    if (myAdjustBorders) {
      UIUtil.removeScrollBorder(each.component)
    }
  }

  override fun sortTabs(comparator: Comparator<in TabInfo>) {
    myVisibleInfos.sort(comparator)
    resetTabsCache()
    relayout(true, false)
  }

  protected fun reorderTab(tabInfo: TabInfo, newIndex: Int) {
    if (myVisibleInfos.remove(tabInfo)) {
      myVisibleInfos.add(newIndex, tabInfo)
      resetTabsCache()
      relayout(true, false)
    }
  }

  override fun setRequestFocusOnLastFocusedComponent(requestFocusOnLastFocusedComponent: Boolean): JBTabsPresentation {
    isRequestFocusOnLastFocusedComponent = requestFocusOnLastFocusedComponent
    return this
  }

  override fun getData(dataId: @NonNls String): Any? {
    if (myDataProvider != null) {
      val value = myDataProvider!!.getData(dataId)
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

  override fun getDataProvider(): DataProvider? {
    return myDataProvider
  }

  override fun setDataProvider(dataProvider: DataProvider): JBTabsImpl {
    myDataProvider = dataProvider
    return this
  }

  private class DefaultDecorator : UiDecorator {
    override fun getDecoration(): UiDecoration {
      return UiDecoration(null, JBInsets(5, 12, 5, 12))
    }
  }

  fun layout(c: JComponent?, bounds: Rectangle): Rectangle {
    val now = c!!.bounds
    if (bounds != now) {
      c.bounds = bounds
    }
    c.doLayout()
    c.putClientProperty(LAYOUT_DONE, java.lang.Boolean.TRUE)
    return bounds
  }

  fun layout(c: JComponent?, x: Int, y: Int, width: Int, height: Int): Rectangle {
    return layout(c, Rectangle(x, y, width, height))
  }

  private fun applyResetComponents() {
    for (i in 0 until componentCount) {
      val each = getComponent(i)
      if (each is JComponent) {
        if (!ClientProperty.isTrue(each, LAYOUT_DONE!!)) {
          layout(each, Rectangle(0, 0, 0, 0))
        }
      }
    }
  }

  override fun setTabLabelActionsMouseDeadzone(length: TimedDeadzone.Length): JBTabsPresentation {
    tabActionsMouseDeadzone = length
    val all = tabs
    for (each in all) {
      val eachLabel = myInfo2Label[each]
      eachLabel!!.updateTabActions()
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

  override fun getTabsPosition(): JBTabsPosition {
    return position
  }

  override fun setTabDraggingEnabled(enabled: Boolean): JBTabsPresentation {
    isTabDraggingEnabled = enabled
    return this
  }

  override fun setAlphabeticalMode(alphabeticalMode: Boolean): JBTabsPresentation {
    isAlphabeticalMode = alphabeticalMode
    return this
  }

  override fun setSupportsCompression(supportsCompression: Boolean): JBTabsPresentation {
    mySupportsCompression = supportsCompression
    updateRowLayout()
    return this
  }

  fun reallocate(source: TabInfo?, target: TabInfo?) {
    if (source == target || source == null || target == null) return
    val targetIndex = myVisibleInfos.indexOf(target)
    myVisibleInfos.remove(source)
    myVisibleInfos.add(targetIndex, source)
    invalidate()
    relayout(true, true)
  }

  val isHorizontalTabs: Boolean
    get() = tabsPosition == JBTabsPosition.top || tabsPosition == JBTabsPosition.bottom

  override fun putInfo(info: MutableMap<in String, in String>) {
    val selected = selectedInfo
    selected?.putInfo(info)
  }

  override fun resetDropOver(tabInfo: TabInfo) {
    if (myDropInfo != null) {
      val dropInfo: TabInfo = myDropInfo
      myDropInfo = null
      myShowDropLocation = true
      myForcedRelayout = true
      setDropInfoIndex(-1)
      setDropSide(-1)
      doRemoveTab(dropInfo, null, true)
    }
  }

  override fun startDropOver(tabInfo: TabInfo, point: RelativePoint): Image {
    myDropInfo = tabInfo
    val pointInMySpace = point.getPoint(this)
    val index = effectiveLayout!!.getDropIndexFor(pointInMySpace)
    setDropInfoIndex(index)
    addTab(myDropInfo!!, index, true, true)
    val label = myInfo2Label[myDropInfo]
    val size = label!!.preferredSize
    label.setBounds(0, 0, size.width, size.height)
    val img = UIUtil.createImage(this, size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    label.paintOffscreen(g)
    g.dispose()
    relayout(true, false)
    return img
  }

  override fun processDropOver(over: TabInfo, point: RelativePoint) {
    val pointInMySpace = point.getPoint(this)
    val index = effectiveLayout!!.getDropIndexFor(pointInMySpace)
    val side: Int
    side = if (myVisibleInfos.isEmpty()) {
      SwingConstants.CENTER
    }
    else {
      if (index != -1) -1 else effectiveLayout!!.getDropSideFor(pointInMySpace)
    }
    if (index != dropInfoIndex) {
      setDropInfoIndex(index)
      relayout(true, false)
    }
    if (side != myDropSide) {
      setDropSide(side)
      relayout(true, false)
    }
  }

  override fun getDropInfoIndex(): Int {
    return myDropInfoIndex
  }

  @MagicConstant(
    intValues = [SwingConstants.CENTER.toLong(), SwingConstants.TOP.toLong(), SwingConstants.LEFT.toLong(), SwingConstants.BOTTOM.toLong(), SwingConstants.RIGHT.toLong(), -1])
  override fun getDropSide(): Int {
    return myDropSide
  }

  override fun isEmptyVisible(): Boolean {
    return myVisibleInfos.isEmpty()
  }

  val tabHGap: Int
    get() = -myBorder.thickness

  override fun toString(): String {
    return "JBTabs visible=$myVisibleInfos selected=$mySelectedInfo"
  }

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
      var name = accessibleName
      if (name == null) {
        name = getClientProperty(ACCESSIBLE_NAME_PROPERTY) as String
      }
      if (name == null) {
        // Similar to JTabbedPane, we return the name of our selected tab
        // as our own name.
        val selectedLabel: TabLabel = selectedLabel
        if (selectedLabel != null) {
          if (selectedLabel.accessibleContext != null) {
            name = selectedLabel.accessibleContext.accessibleName
          }
        }
      }
      if (name == null) {
        name = super.getAccessibleName()
      }
      return name
    }

    override fun getAccessibleRole(): AccessibleRole {
      return AccessibleRole.PAGE_TAB_LIST
    }

    override fun getAccessibleChild(i: Int): Accessible {
      val accessibleChild = super.getAccessibleChild(i)
      // Note: Unlike a JTabbedPane, JBTabsImpl has many more child types than just pages.
      // So we wrap TabLabel instances with their corresponding AccessibleTabPage, while
      // leaving other types of children untouched.
      return if (accessibleChild is TabLabel) {
        myInfo2Page[accessibleChild.info]!!
      }
      else accessibleChild
    }

    override fun getAccessibleSelection(): AccessibleSelection {
      return this
    }

    override fun getAccessibleSelectionCount(): Int {
      return if (selectedInfo == null) 0 else 1
    }

    override fun getAccessibleSelection(i: Int): Accessible {
      return if (selectedInfo == null) {
        null
      }
      else myInfo2Page[selectedInfo]!!
    }

    override fun isAccessibleChildSelected(i: Int): Boolean {
      return i == getIndexOf(selectedInfo)
    }

    override fun addAccessibleSelection(i: Int) {
      val info = getTabAt(i)
      select(info, false)
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
  private inner class AccessibleTabPage internal constructor(private val tabInfo: TabInfo) : AccessibleContext(), Accessible, AccessibleComponent, AccessibleAction {
    private val myParent: JBTabsImpl
    private val myComponent: Component

    init {
      myParent = this@JBTabsImpl
      myComponent = tabInfo.component
      setAccessibleParent(myParent)
      initAccessibleContext()
    }

    private val tabIndex: Int
      private get() = getIndexOf(tabInfo)
    private val tabLabel: TabLabel?
      private get() = myInfo2Label[tabInfo]

    /*
     * initializes the AccessibleContext for the page
     */
    fun initAccessibleContext() {
      // Note: null checks because we do not want to load Accessibility classes unnecessarily.
      if (accessibleContext != null && myComponent is Accessible) {
        val ac = myComponent.getAccessibleContext()
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
        name = getClientProperty(ACCESSIBLE_NAME_PROPERTY) as String
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
        description = getClientProperty(ACCESSIBLE_DESCRIPTION_PROPERTY) as String
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

    override fun getAccessibleRole(): AccessibleRole {
      return AccessibleRole.PAGE_TAB
    }

    override fun getAccessibleStateSet(): AccessibleStateSet {
      val states = myParent.getAccessibleContext().accessibleStateSet
      states.add(AccessibleState.SELECTABLE)
      val info = myParent.selectedInfo
      if (info == tabInfo) {
        states.add(AccessibleState.SELECTED)
      }
      return states
    }

    override fun getAccessibleIndexInParent(): Int {
      return tabIndex
    }

    override fun getAccessibleChildrenCount(): Int {
      // Expose the tab content only if it is active, as the content for
      // inactive tab does is usually not ready (i.e. may never have been
      // activated).
      return if (selectedInfo == tabInfo && myComponent is Accessible) 1 else 0
    }

    override fun getAccessibleChild(i: Int): Accessible {
      return if (selectedInfo == tabInfo && myComponent is Accessible) (myComponent as Accessible) else null
    }

    override fun getLocale(): Locale {
      return locale
    }

    override fun getAccessibleComponent(): AccessibleComponent {
      return this
    }

    override fun getAccessibleAction(): AccessibleAction {
      return this
    }

    // AccessibleComponent methods
    override fun getBackground(): Color {
      return background
    }

    override fun setBackground(c: Color) {
      background = c
    }

    override fun getForeground(): Color {
      return foreground
    }

    override fun setForeground(c: Color) {
      foreground = c
    }

    override fun getCursor(): Cursor {
      return cursor
    }

    override fun setCursor(c: Cursor) {
      cursor = c
    }

    override fun getFont(): Font {
      return font
    }

    override fun setFont(f: Font) {
      font = f
    }

    override fun getFontMetrics(f: Font): FontMetrics {
      return this@JBTabsImpl.getFontMetrics(f)
    }

    override fun isEnabled(): Boolean {
      return tabInfo.isEnabled
    }

    override fun setEnabled(b: Boolean) {
      tabInfo.isEnabled = b
    }

    override fun isVisible(): Boolean {
      return !tabInfo.isHidden
    }

    override fun setVisible(b: Boolean) {
      tabInfo.isHidden = !b
    }

    override fun isShowing(): Boolean {
      return this@JBTabsImpl.isShowing
    }

    override fun contains(p: Point): Boolean {
      val r = bounds
      return r.contains(p)
    }

    override fun getLocationOnScreen(): Point {
      val parentLocation = this@JBTabsImpl.locationOnScreen
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
    override fun getBounds(): Rectangle {
      return tabLabel!!.bounds
    }

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

    override fun getAccessibleAt(p: Point): Accessible {
      return if (myComponent is Accessible) (myComponent as Accessible) else null
    }

    override fun isFocusTraversable(): Boolean {
      return false
    }

    override fun requestFocus() {
      // do nothing
    }

    override fun addFocusListener(l: FocusListener) {
      // do nothing
    }

    override fun removeFocusListener(l: FocusListener) {
      // do nothing
    }

    override fun getAccessibleIcon(): Array<AccessibleIcon> {
      var accessibleIcon: AccessibleIcon? = null
      if (tabInfo.icon is ImageIcon) {
        val ac = (tabInfo.icon as ImageIcon).accessibleContext
        accessibleIcon = ac as AccessibleIcon
      }
      return if (accessibleIcon != null) {
        val returnIcons = arrayOfNulls<AccessibleIcon>(1)
        returnIcons[0] = accessibleIcon
        returnIcons
      }
      else {
        null
      }
    }

    // AccessibleAction methods
    override fun getAccessibleActionCount(): Int {
      return 1
    }

    override fun getAccessibleActionDescription(i: Int): String {
      return if (i != 0) {
        null
      }
      else "Activate"
    }

    override fun doAccessibleAction(i: Int): Boolean {
      if (i != 0) {
        return false
      }
      select(tabInfo, true)
      return true
    }
  }

  @Deprecated("Not used.")
  fun dispose() {
  }

  private inner class TitleAction private constructor(private val myTitleProvider: Producer<out Pair<Icon, String>>) : AnAction(), CustomComponentAction {
    private val myLabel: JLabel = object : JLabel() {
      override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        size.height = JBUI.scale(SingleHeightTabs.UNSCALED_PREF_HEIGHT)
        return size
      }

      override fun updateUI() {
        super.updateUI()
        font = TabLabel(this@JBTabsImpl, TabInfo(null)).labelComponent.font
        border = JBUI.Borders.empty(0, 5, 0, 6)
      }
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      update()
      return myLabel
    }

    private fun update() {
      val pair = myTitleProvider.produce()
      myLabel.icon = pair.first
      myLabel.text = pair.second
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
      //do nothing
    }

    override fun update(e: AnActionEvent) {
      update()
    }
  }

  companion object {
    val PINNED = Key.create<Boolean>("pinned")
    val SIDE_TABS_SIZE_LIMIT_KEY = Key.create<Int>("SIDE_TABS_SIZE_LIMIT_KEY")
    private val HIDDEN_INFOS_SELECT_INDEX_KEY = Key.create<Int>("HIDDEN_INFOS_SELECT_INDEX")
    val MIN_TAB_WIDTH = scale(75)
    val DEFAULT_MAX_TAB_WIDTH = scale(300)
    private val ABC_COMPARATOR = Comparator { o1: TabInfo, o2: TabInfo -> StringUtil.naturalCompare(o1.text, o2.text) }
    private val LOG = Logger.getInstance(JBTabsImpl::class.java)
    private const val SCROLL_BAR_THICKNESS = 3
    val ourDefaultDecorator: UiDecorator = DefaultDecorator()
    private const val myAdjustBorders = true
    private val LAYOUT_DONE: @NonNls String? = "Layout.done"
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

    private val focusOwner: JComponent?
      private get() {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        return owner as? JComponent
      }

    private fun updateToolbarIfVisibilityChanged(toolbar: ActionToolbar?, previousBounds: Rectangle) {
      if (toolbar == null) return
      val curBounds = toolbar.component.bounds
      if (curBounds.isEmpty != previousBounds.isEmpty) {
        toolbar.updateActionsImmediately()
      }
    }

    private val arcSize: Int
      private get() = 4

    private fun sortTabsAlphabetically(tabs: List<TabInfo>) {
      val lastPinnedIndex = ContainerUtil.lastIndexOf(tabs) { obj: TabInfo -> obj.isPinned }
      if (lastPinnedIndex == -1 || !getBoolean("editor.keep.pinned.tabs.on.left")) {
        tabs.sort(ABC_COMPARATOR)
      }
      else {
        tabs.subList(0, lastPinnedIndex + 1).sort(ABC_COMPARATOR)
        tabs.subList(lastPinnedIndex + 1, tabs.size).sort(ABC_COMPARATOR)
      }
    }

    val selectionTabVShift: Int
      get() = 2

    private fun isToDeferRemoveForLater(c: JComponent): Boolean {
      return c.rootPane != null
    }

    private fun isChanged(oldObject: Any?, newObject: Any?): Boolean {
      return !Comparing.equal(oldObject, newObject)
    }

    fun isSelectionClick(e: MouseEvent): Boolean {
      if (e.clickCount == 1) {
        if (!e.isPopupTrigger) {
          return e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isAltDown && !e.isMetaDown
        }
      }
      return false
    }

    fun resetLayout(c: JComponent?) {
      if (c == null) return
      c.putClientProperty(LAYOUT_DONE, null)
    }
  }
}
