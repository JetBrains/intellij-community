// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package com.intellij.openapi.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Weighted
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.ClickListener
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScreenUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Predicate
import javax.swing.*
import kotlin.math.max

open class ThreeComponentsSplitter @JvmOverloads constructor(vertical: Boolean = false,
                                                             onePixelDividers: Boolean = false) : JPanel() {
  private var isLookAndFeelUpdated = false

  /**
   * /------/
   * |  1   |
   * This is vertical split |------|
   * |  2   |
   * /------/
   *
   * /-------/
   * |   |   |
   * This is horizontal split | 1 | 2 |
   * |   |   |
   * /-------/
   */
  private var verticalSplit = false
  private var isHonorMinimumSize: Boolean = false
  private val firstDivider: Divider
  private val lastDivider: Divider
  private var dividerDispatcher: EventDispatcher<ComponentListener>? = null

  var firstComponent: JComponent? = null
    /**
     * Sets component which is located as the "first" split area. The method doesn't validate and
     * repaint the splitter if there is one already.
     *
     */
    set(component) {
      if (field === component) {
        return
      }
      if (field != null) {
        remove(field)
      }
      field = component
      doAddComponent(component)
    }

  var innerComponent: JComponent? = null
    /**
     * Sets component which is located as the "inner" split area.
     * The method doesn't validate and repaint the splitter.
     */
    set(component) {
      if (field === component) {
        return
      }
      if (field != null) {
        remove(field)
      }
      field = component
      doAddComponent(component)
    }

  var lastComponent: JComponent? = null
    /**
     * Sets component which is located as the "second" split area. The method doesn't validate and
     * repaint the splitter.
     */
    set(component) {
      if (field === component) {
        return
      }
      if (field != null) {
        remove(field)
      }
      field = component
      doAddComponent(component)
    }

  private var showDividerControls = false
  private var dividerZone = 0

  private inner class MyFocusTraversalPolicy : LayoutFocusTraversalPolicy() {
    override fun getComponentAfter(aContainer: Container, aComponent: Component): Component {
      val comp: Component
      if (SwingUtilities.isDescendingFrom(aComponent, firstComponent)) {
        val next = nextVisible(firstComponent)
        comp = if (next != null) findChildToFocus(next) else aComponent
      }
      else if (SwingUtilities.isDescendingFrom(aComponent, innerComponent)) {
        val next = nextVisible(innerComponent)
        comp = if (next != null) findChildToFocus(next) else aComponent
      }
      else {
        val next = nextVisible(lastComponent)
        comp = if (next != null) findChildToFocus(next) else aComponent
      }
      return if (comp === aComponent) {
        // if focus is stuck on the component let it go further
        super.getComponentAfter(aContainer, aComponent)
      }
      else comp
    }

    override fun getComponentBefore(aContainer: Container, aComponent: Component): Component {
      val component: Component
      if (SwingUtilities.isDescendingFrom(aComponent, innerComponent)) {
        val prev = prevVisible(innerComponent)
        component = if (prev != null) findChildToFocus(prev) else aComponent
      }
      else if (SwingUtilities.isDescendingFrom(aComponent, lastComponent)) {
        val prev = prevVisible(lastComponent)
        component = if (prev != null) findChildToFocus(prev) else aComponent
      }
      else {
        val prev = prevVisible(firstComponent)
        component = if (prev != null) findChildToFocus(prev) else aComponent
      }
      return if (component === aComponent) {
        // if focus is stuck on the component let it go further
        super.getComponentBefore(aContainer, aComponent)
      }
      else component
    }

    private fun nextVisible(component: Component?): Component? {
      if (component === firstComponent) {
        return if (innerVisible()) innerComponent else if (lastVisible()) lastComponent else null
      }
      if (component === innerComponent) {
        return if (lastVisible()) lastComponent else if (firstVisible()) firstComponent else null
      }
      return if (component === lastComponent) if (firstVisible()) firstComponent else if (innerVisible()) innerComponent else null else null
    }

    private fun prevVisible(component: Component?): Component? {
      if (component === firstComponent) {
        return if (lastVisible()) lastComponent else if (innerVisible()) innerComponent else null
      }
      if (component === innerComponent) {
        return if (firstVisible()) firstComponent else if (lastVisible()) lastComponent else null
      }
      return if (component === lastComponent) if (innerVisible()) innerComponent else if (firstVisible()) firstComponent else null else null
    }

    override fun getFirstComponent(aContainer: Container): Component? {
      if (firstVisible()) {
        return findChildToFocus(firstComponent)
      }
      return findChildToFocus(nextVisible(firstComponent) ?: return null)
    }

    override fun getLastComponent(aContainer: Container): Component? {
      if (lastVisible()) {
        return findChildToFocus(lastComponent)
      }
      return findChildToFocus(prevVisible(lastComponent) ?: return null)
    }

    private var reentrantLock = false

    override fun getDefaultComponent(aContainer: Container): Component? {
      if (reentrantLock) {
        return null
      }

      try {
        reentrantLock = true
        if (innerVisible()) {
          return findChildToFocus(innerComponent)
        }
        else {
          return findChildToFocus(nextVisible(lastComponent) ?: return null)
        }
      }
      finally {
        reentrantLock = false
      }
    }

    fun findChildToFocus(component: Component?): Component {
      val ancestor = SwingUtilities.getWindowAncestor(this@ThreeComponentsSplitter)
      // Step 1 : We should take into account cases with detached toolwindows and editors
      //       - find the recent focus owner for the window of the splitter and
      //         make sure that the most recently focused component is inside the
      //         passed component. By the way, the recent focused component is supposed to be focusable
      FocusUtil.getMostRecentComponent(component, ancestor)?.let {
        return it
      }

      // Step 2 : If the best candidate to focus is a panel, usually it does not
      //          have focus representation for showing the focused state
      //          Let's ask the focus traversal policy what is the best candidate
      val defaultComponentInPanel = FocusUtil.getDefaultComponentInPanel(component)
      return defaultComponentInPanel ?: FocusUtil.findFocusableComponentIn(component, null)

      //Step 3 : Return the component, but find the first focusable component first
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #ThreeComponentsSplitter()}")
  constructor(@Suppress("UNUSED_PARAMETER") parentDisposable: Disposable) : this(false)

  var dividerWidth: Int = if (onePixelDividers) 1 else 7
    set(width) {
      if (width < 0) {
        throw IllegalArgumentException("Wrong divider width: $width")
      }
      if (field != width) {
        field = width
        doLayout()
        repaint()
      }
    }

  /**
   * Creates horizontal split with proportion equals to .5f
   */
  init {
    verticalSplit = vertical
    showDividerControls = false
    @Suppress("LeakingThis")
    firstDivider = Divider(splitter = this, isFirst = true, isOnePixel = onePixelDividers)
    @Suppress("LeakingThis")
    lastDivider = Divider(splitter = this, isFirst = false, isOnePixel = onePixelDividers)
    @Suppress("LeakingThis")
    setFocusCycleRoot(true)
    @Suppress("LeakingThis")
    setFocusTraversalPolicy(MyFocusTraversalPolicy())
    @Suppress("LeakingThis")
    setOpaque(false)
    @Suppress("LeakingThis")
    add(firstDivider)
    @Suppress("LeakingThis")
    add(lastDivider)
  }

  override fun updateUI() {
    super.updateUI()

    // if null, it means that `updateUI` is called as a part of init
    @Suppress("SENSELESS_COMPARISON")
    if (firstDivider != null) {
      isLookAndFeelUpdated = true
    }
  }

  fun setShowDividerControls(showDividerControls: Boolean) {
    this.showDividerControls = showDividerControls
    orientation = verticalSplit
  }

  fun setDividerMouseZoneSize(size: Int) {
    dividerZone = scale(size)
  }

  fun setHonorComponentsMinimumSize(honorMinimumSize: Boolean) {
    isHonorMinimumSize = honorMinimumSize
  }

  override fun isVisible(): Boolean = super.isVisible() && (firstVisible() || innerVisible() || lastVisible())

  protected fun lastVisible(): Boolean = !Splitter.isNull(lastComponent) && lastComponent!!.isVisible

  private fun innerVisible(): Boolean = !Splitter.isNull(innerComponent) && innerComponent!!.isVisible

  protected fun firstVisible(): Boolean = !Splitter.isNull(firstComponent) && firstComponent!!.isVisible

  private fun visibleDividersCount(): Int {
    var count = 0
    if (firstDividerVisible()) {
      count++
    }
    if (lastDividerVisible()) {
      count++
    }
    return count
  }

  private fun firstDividerVisible(): Boolean {
    return firstVisible() && innerVisible() || firstVisible() && lastVisible() && !innerVisible()
  }

  private fun lastDividerVisible(): Boolean = innerVisible() && lastVisible()

  override fun getMinimumSize(): Dimension {
    if (isHonorMinimumSize) {
      val dividerWidth = dividerWidth
      val firstSize = firstComponent?.getMinimumSize() ?: JBUI.emptySize()
      val lastSize = lastComponent?.getMinimumSize() ?: JBUI.emptySize()
      val innerSize = innerComponent?.getMinimumSize() ?: JBUI.emptySize()
      if (orientation) {
        val width = max(firstSize.width, max(lastSize.width, innerSize.width))
        var height = visibleDividersCount() * dividerWidth
        height += firstSize.height
        height += lastSize.height
        height += innerSize.height
        return Dimension(width, height)
      }
      else {
        val height = max(firstSize.height, max(lastSize.height, innerSize.height))
        var width = visibleDividersCount() * dividerWidth
        width += firstSize.width
        width += lastSize.width
        width += innerSize.width
        return Dimension(width, height)
      }
    }
    return super.getMinimumSize()
  }

  override fun doLayout() {
    val width = width
    val height = height
    val firstRect = Rectangle()
    val firstDividerRect = Rectangle()
    val lastDividerRect = Rectangle()
    val lastRect = Rectangle()
    val innerRect = Rectangle()
    val componentSize = if (orientation) height else width
    var dividerWidth = dividerWidth
    val dividersCount = visibleDividersCount()
    var firstComponentSize: Int
    var lastComponentSize: Int
    var innerComponentSize: Int
    if (componentSize <= dividersCount * dividerWidth) {
      firstComponentSize = 0
      lastComponentSize = 0
      innerComponentSize = 0
      dividerWidth = componentSize
    }
    else {
      firstComponentSize = firstSize
      lastComponentSize = lastSize
      val sizeLack = firstComponentSize + lastComponentSize - (componentSize - dividersCount * dividerWidth - minSize)
      if (sizeLack > 0) {
        // Lacking size. Reduce first & last component's size, inner -> MIN_SIZE
        val firstSizeRatio = firstComponentSize.toDouble() / (firstComponentSize + lastComponentSize)
        if (firstComponentSize > 0) {
          firstComponentSize -= (sizeLack * firstSizeRatio).toInt()
          firstComponentSize = max(minSize, firstComponentSize)
        }
        if (lastComponentSize > 0) {
          lastComponentSize -= (sizeLack * (1 - firstSizeRatio)).toInt()
          lastComponentSize = max(minSize, lastComponentSize)
        }
        innerComponentSize = getMinSize(innerComponent)
      }
      else {
        innerComponentSize = max(getMinSize(innerComponent),
                                 (componentSize - dividersCount * dividerWidth - firstSize - lastSize))
      }
      if (!innerVisible()) {
        lastComponentSize += innerComponentSize
        innerComponentSize = 0
        if (!lastVisible()) {
          firstComponentSize = componentSize
        }
      }
    }
    var space = firstComponentSize
    if (orientation) {
      firstRect.setBounds(0, 0, width, firstComponentSize)
      if (firstDividerVisible()) {
        firstDividerRect.setBounds(0, space, width, dividerWidth)
        space += dividerWidth
      }
      innerRect.setBounds(0, space, width, innerComponentSize)
      space += innerComponentSize
      if (lastDividerVisible()) {
        lastDividerRect.setBounds(0, space, width, dividerWidth)
        space += dividerWidth
      }
      lastRect.setBounds(0, space, width, lastComponentSize)
    }
    else {
      firstRect.setBounds(0, 0, firstComponentSize, height)
      if (firstDividerVisible()) {
        firstDividerRect.setBounds(space, 0, dividerWidth, height)
        space += dividerWidth
      }
      innerRect.setBounds(space, 0, innerComponentSize, height)
      space += innerComponentSize
      if (lastDividerVisible()) {
        lastDividerRect.setBounds(space, 0, dividerWidth, height)
        space += dividerWidth
      }
      lastRect.setBounds(space, 0, lastComponentSize, height)
    }
    firstDivider.isVisible = firstDividerVisible()
    firstDivider.bounds = firstDividerRect
    firstDivider.doLayout()
    lastDivider.isVisible = lastDividerVisible()
    lastDivider.bounds = lastDividerRect
    lastDivider.doLayout()
    validateIfNeeded(firstComponent, firstRect)
    validateIfNeeded(innerComponent, innerRect)
    validateIfNeeded(lastComponent, lastRect)
  }

  /**
   * @return `true` if splitter has vertical orientation, `false` otherwise
   */
  var orientation: Boolean
    get() = verticalSplit
    set(verticalSplit) {
      this.verticalSplit = verticalSplit
      firstDivider.setOrientation(verticalSplit)
      lastDivider.setOrientation(verticalSplit)
      doLayout()
      repaint()
    }

  private fun doAddComponent(component: JComponent?) {
    if (component == null) {
      return
    }

    if (isLookAndFeelUpdated) {
      updateComponentTreeUI(component)
      isLookAndFeelUpdated = false
    }
    add(component)
    component.invalidate()
  }

  var minSize: Int = 0
    set(minSize) {
      field = max(0, minSize)
      doLayout()
      repaint()
    }

  var firstSize: Int = 0
    get() = if (firstVisible()) field else 0
    set(size) {
      val oldSize = field
      field = max(getMinSize(true), size)
      if (firstVisible() && oldSize != field) {
        doLayout()
        repaint()
      }
    }

  var lastSize: Int = 0
    get() = if (lastVisible()) field else 0
    set(size) {
      val oldSize = field
      field = max(getMinSize(false), size)
      if (lastVisible() && oldSize != field) {
        doLayout()
        repaint()
      }
    }

  fun getMinSize(first: Boolean): Int {
    return getMinSize(if (first) firstComponent else lastComponent)
  }

  fun getMaxSize(first: Boolean): Int {
    val size = if (orientation) height else width
    return size - (if (first) lastSize else firstSize) - minSize
  }

  private fun getMinSize(component: JComponent?): Int {
    if (isHonorMinimumSize && component != null && component.isVisible) {
      return if (orientation) component.getMinimumSize().height else component.getMinimumSize().width
    }
    else {
      return minSize
    }
  }

  fun addDividerResizeListener(listener: ComponentListener) {
    if (dividerDispatcher == null) {
      dividerDispatcher = EventDispatcher.create(ComponentListener::class.java)
    }
    dividerDispatcher!!.addListener(listener)
  }

  private var glassPane: IdeGlassPane? = null
  private var glassPaneDisposable: Disposable? = null

  override fun addNotify() {
    super.addNotify()
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      initGlassPane()
    }
  }

  override fun removeNotify() {
    super.removeNotify()
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      releaseGlassPane()
    }
  }

  private fun initGlassPane() {
    val glassPane = IdeGlassPaneUtil.find(this)
    if (glassPane === this.glassPane) {
      return
    }

    releaseGlassPane()
    this.glassPane = glassPane
    val glassPaneDisposable = Disposer.newDisposable()
    this.glassPaneDisposable = glassPaneDisposable

    val listener = MyMouseAdapter(listOf(firstDivider, lastDivider))
    glassPane.addMouseMotionPreprocessor(listener, glassPaneDisposable)
    glassPane.addMousePreprocessor(listener, glassPaneDisposable)
  }

  private fun releaseGlassPane() {
    if (glassPaneDisposable != null) {
      Disposer.dispose(glassPaneDisposable!!)
      glassPaneDisposable = null
      glassPane = null
    }
  }

  private class Divider(private val splitter: ThreeComponentsSplitter,
                        private val isFirst: Boolean,
                        private val isOnePixel: Boolean) : JPanel(GridBagLayout()) {
    private var isDragging = false
    private var point: Point? = null

    fun getTargetEvent(e: MouseEvent): MouseEvent = SwingUtilities.convertMouseEvent(e.component, e, this)

    private var wasPressedOnMe = false

    init {
      setFocusable(false)
      enableEvents(AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
      setOrientation(splitter.verticalSplit)
    }

    override fun getBackground(): Color? = if (isOnePixel) UIUtil.CONTRAST_BORDER_COLOR else super.getBackground()

    private fun isInside(p: Point): Boolean {
      if (!isVisible) {
        return false
      }

      val window = ComponentUtil.getWindow(this)
      if (window != null) {
        val point = SwingUtilities.convertPoint(this, p, window)
        val component = UIUtil.getDeepestComponentAt(window, point.x, point.y)
        val components = listOf<Component?>(splitter.firstComponent, splitter.firstDivider, splitter.innerComponent,
                                            splitter.lastDivider, splitter.lastComponent)
        if (ComponentUtil.findParentByCondition(component, Predicate { it != null && components.contains(it) }) == null) {
          return false
        }
      }
      val dndOff = if (isOnePixel) scale(Registry.intValue("ide.splitter.mouseZone")) / 2 else 0
      if (splitter.verticalSplit) {
        if (p.x >= 0 && p.x < width) {
          return if (height > 0) {
            p.y >= -dndOff && p.y < height + dndOff
          }
          else {
            p.y >= -splitter.dividerZone / 2 && p.y <= splitter.dividerZone / 2
          }
        }
      }
      else {
        if (p.y >= 0 && p.y < height) {
          return if (width > 0) {
            p.x >= -dndOff && p.x < width + dndOff
          }
          else {
            p.x >= -splitter.dividerZone / 2 && p.x <= splitter.dividerZone / 2
          }
        }
      }
      return false
    }

    fun setOrientation(isVerticalSplit: Boolean) {
      removeAll()
      if (!splitter.showDividerControls) {
        return
      }
      val xMask = if (isVerticalSplit) 1 else 0
      val yMask = if (isVerticalSplit) 0 else 1
      val glueIcon = if (isVerticalSplit) SplitGlueV else AllIcons.General.ArrowSplitCenterH
      val glueFill = if (isVerticalSplit) GridBagConstraints.VERTICAL else GridBagConstraints.HORIZONTAL
      add(JLabel(glueIcon),
          GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, if (isVerticalSplit) GridBagConstraints.EAST else GridBagConstraints.NORTH, glueFill,
                             JBInsets.emptyInsets(), 0, 0))
      val splitDownlabel = JLabel(if (isVerticalSplit) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight)
      splitDownlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      splitDownlabel.setToolTipText(if (isVerticalSplit) UIBundle.message("splitter.down.tooltip.text")
                                    else UIBundle
        .message("splitter.right.tooltip.text"))
      object : ClickListener() {
        override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
          if (splitter.innerComponent != null) {
            val income = if (splitter.verticalSplit) splitter.innerComponent!!.height else splitter.innerComponent!!.width
            if (isFirst) {
              splitter.firstSize = splitter.firstSize + income
            }
            else {
              splitter.lastSize = splitter.lastSize + income
            }
          }
          return true
        }
      }.installOn(splitDownlabel)
      add(splitDownlabel,
          GridBagConstraints(if (isVerticalSplit) 1 else 0,
                             if (isVerticalSplit) 0 else 5,
                             1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0))
      //
      add(JLabel(glueIcon),
          GridBagConstraints(2 * xMask, 2 * yMask, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, glueFill, JBInsets.emptyInsets(), 0, 0))
      val splitCenterlabel = JLabel(if (isVerticalSplit) AllIcons.General.ArrowSplitCenterV else AllIcons.General.ArrowSplitCenterH)
      splitCenterlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      splitCenterlabel.setToolTipText(UIBundle.message("splitter.center.tooltip.text"))
      object : ClickListener() {
        override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
          center()
          return true
        }
      }.installOn(splitCenterlabel)
      add(splitCenterlabel,
          GridBagConstraints(3 * xMask, 3 * yMask, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                             JBInsets.emptyInsets(), 0, 0))
      add(JLabel(glueIcon),
          GridBagConstraints(4 * xMask, 4 * yMask, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, glueFill, JBInsets.emptyInsets(), 0, 0))
      //
      val splitUpLabel = JLabel(if (isVerticalSplit) AllIcons.General.ArrowUp else AllIcons.General.ArrowLeft)
      splitUpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      splitUpLabel.setToolTipText(if (isVerticalSplit) UIBundle.message("splitter.up.tooltip.text")
                                  else UIBundle
        .message("splitter.left.tooltip.text"))
      object : ClickListener() {
        override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
          if (splitter.innerComponent != null) {
            val income = if (splitter.verticalSplit) splitter.innerComponent!!.height else splitter.innerComponent!!.width
            if (isFirst) {
              splitter.firstSize = splitter.firstSize + income
            }
            else {
              splitter.lastSize = splitter.lastSize + income
            }
          }
          return true
        }
      }.installOn(splitUpLabel)
      add(splitUpLabel,
          GridBagConstraints(if (isVerticalSplit) 5 else 0,
                             if (isVerticalSplit) 0 else 1,
                             1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0))
      add(JLabel(glueIcon),
          GridBagConstraints(6 * xMask, 6 * yMask, 1, 1, 0.0, 0.0,
                             if (isVerticalSplit) GridBagConstraints.WEST else GridBagConstraints.SOUTH, glueFill, JBInsets.emptyInsets(),
                             0, 0))
    }

    private fun center() {
      if (splitter.innerComponent != null) {
        val total = splitter.firstSize + if (splitter.verticalSplit) splitter.innerComponent!!.height else splitter.innerComponent!!.width
        if (isFirst) {
          splitter.firstSize = total / 2
        }
        else {
          splitter.lastSize = total / 2
        }
      }
    }

    public override fun processMouseMotionEvent(e: MouseEvent) {
      super.processMouseMotionEvent(e)
      if (!isShowing()) {
        return
      }

      val glassPane = splitter.glassPane
      if (MouseEvent.MOUSE_DRAGGED == e.id && wasPressedOnMe) {
        isDragging = true
        setCursor(resizeCursor)
        glassPane?.setCursor(resizeCursor, this)
        point = SwingUtilities.convertPoint(this, e.getPoint(), splitter)
        val size = if (splitter.orientation) splitter.height else splitter.width
        if (splitter.orientation) {
          if (size > 0 || splitter.dividerZone > 0) {
            if (isFirst) {
              splitter.firstSize = clamp(point!!.y, size, splitter.firstComponent, splitter.lastSize)
            }
            else {
              splitter.lastSize = clamp(size - point!!.y - splitter.dividerWidth, size, splitter.lastComponent, splitter.firstSize)
            }
          }
        }
        else {
          if (size > 0 || splitter.dividerZone > 0) {
            if (isFirst) {
              splitter.firstSize = clamp(point!!.x, size, splitter.firstComponent, splitter.lastSize)
            }
            else {
              splitter.lastSize = clamp(size - point!!.x - splitter.dividerWidth, size, splitter.lastComponent, splitter.firstSize)
            }
          }
        }
        splitter.doLayout()
      }
      else if (MouseEvent.MOUSE_MOVED == e.id) {
        if (glassPane != null) {
          if (isInside(e.getPoint())) {
            glassPane.setCursor(resizeCursor, this)
            e.consume()
          }
          else {
            glassPane.setCursor(null, this)
          }
        }
      }
      if (wasPressedOnMe) {
        e.consume()
      }
    }

    private fun clamp(pos: Int, size: Int, component: JComponent?, componentSize: Int): Int {
      val minSize = splitter.getMinSize(component)
      val maxSize = size - componentSize -
                    splitter.getMinSize(splitter.innerComponent) - splitter.dividerWidth * splitter.visibleDividersCount()
      return if (minSize <= maxSize) pos.coerceIn(minSize, maxSize) else pos
    }

    public override fun processMouseEvent(e: MouseEvent) {
      super.processMouseEvent(e)
      if (!isShowing()) {
        return
      }

      val glassPane = splitter.glassPane
      when (e.id) {
        MouseEvent.MOUSE_ENTERED -> setCursor(resizeCursor)
        MouseEvent.MOUSE_EXITED -> {
          if (!isDragging) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
          }
        }
        MouseEvent.MOUSE_PRESSED -> {
          if (isInside(e.getPoint())) {
            wasPressedOnMe = true
            glassPane?.setCursor(resizeCursor, this)
            e.consume()
          }
          else {
            wasPressedOnMe = false
          }
        }
        MouseEvent.MOUSE_RELEASED -> {
          if (wasPressedOnMe) {
            e.consume()
          }
          if (isInside(e.getPoint()) && glassPane != null) {
            glassPane.setCursor(resizeCursor, this)
          }
          if (isDragging && splitter.dividerDispatcher != null) {
            splitter.dividerDispatcher!!.getMulticaster().componentResized(ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED))
          }
          wasPressedOnMe = false
          isDragging = false
          point = null
        }
        MouseEvent.MOUSE_CLICKED -> {
          if (e.clickCount == 2) {
            center()
          }
        }
      }
    }

    private val resizeCursor: Cursor
      get() {
        if (splitter.orientation) {
          return Cursor.getPredefinedCursor(if (isFirst) Cursor.S_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR)
        }
        else {
          return Cursor.getPredefinedCursor(if (isFirst) Cursor.W_RESIZE_CURSOR else Cursor.E_RESIZE_CURSOR)
        }
      }
  }

  private class MyMouseAdapter(private val dividers: List<Divider>) : MouseAdapter(), Weighted {
    override fun mousePressed(e: MouseEvent) {
      processMouseEvent(e)
    }

    override fun mouseReleased(e: MouseEvent) {
      processMouseEvent(e)
    }

    override fun mouseMoved(e: MouseEvent) {
      processMouseMotionEvent(e)
    }

    override fun mouseDragged(e: MouseEvent) {
      processMouseMotionEvent(e)
    }

    override fun getWeight(): Double = 1.0

    private fun processMouseMotionEvent(e: MouseEvent) {
      for (divider in dividers) {
        val event = divider.getTargetEvent(e)
        divider.processMouseMotionEvent(event)
        if (event.isConsumed) {
          e.consume()
          break
        }
      }
    }

    private fun processMouseEvent(e: MouseEvent) {
      for (divider in dividers) {
        val event = divider.getTargetEvent(e)
        divider.processMouseEvent(event)
        if (event.isConsumed) {
          e.consume()
          break
        }
      }
    }
  }
}

private val SplitGlueV = EmptyIcon.create(17, 6)

private fun validateIfNeeded(c: JComponent?, rect: Rectangle) {
  if (c != null && !Splitter.isNull(c)) {
    if (c.bounds != rect) {
      c.bounds = rect
      c.revalidate()
    }
  }
  else {
    Splitter.hideNull(c)
  }
}

private fun updateComponentTreeUI(rootComponent: JComponent?) {
  for (component in UIUtil.uiTraverser(rootComponent).postOrderDfsTraversal()) {
    if (component is JComponent) {
      component.updateUI()
    }
  }
}