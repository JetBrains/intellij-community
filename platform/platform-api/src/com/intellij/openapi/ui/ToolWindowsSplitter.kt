// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Weighted
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.FocusUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.*
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * @author Matt Coster
 */
class ToolWindowsSplitter(
  toolWindowsLayout: ToolWindowsLayout,
  parentDisposable: Disposable?,
  onePixelDividers: Boolean,
) : JPanel(), Disposable {
  companion object {
    internal val SplitGlueV: Icon = EmptyIcon.create(17, 6)
  }

  var dividerWidth = if (onePixelDividers) 1 else 7
    set(value) {
      if (value < 0) throw IllegalArgumentException("Wrong divider width: $value")
      if (field != value) {
        field = value
        doLayout()
        repaint()
      }
    }

  var toolWindowsLayout = toolWindowsLayout
    set(value) {
      field = value
      doLayout()
      repaint()
    }

  var honorMinimumSize = false
    private set

  private var leftDivider: Divider
  private var rightDivider: Divider
  private var topDivider: Divider
  private var bottomDivider: Divider
  private val dividerDispatcherDelegate = lazy { EventDispatcher.create(ComponentListener::class.java) }
  private val dividerDispatcher by dividerDispatcherDelegate

  var leftToolWindowsComponent: JComponent? = null
    set(value) {
      if (field === value) return
      field?.let { remove(it) }
      field = value
      value?.let { doAddComponent(it) }
    }

  var rightToolWindowsComponent: JComponent? = null
    set(value) {
      if (field === value) return
      field?.let { remove(it) }
      field = value
      value?.let { doAddComponent(it) }
    }

  var topToolWindowsComponent: JComponent? = null
    set(value) {
      if (field === value) return
      field?.let { remove(it) }
      field = value
      value?.let { doAddComponent(it) }
    }

  var bottomToolWindowsComponent: JComponent? = null
    set(value) {
      if (field === value) return
      field?.let { remove(it) }
      field = value
      value?.let { doAddComponent(it) }
    }

  var documentComponent: JComponent? = null
    set(value) {
      if (field === value) return
      field?.let { remove(it) }
      field = value
      value?.let { doAddComponent(it) }
    }

  private fun doAddComponent(component: JComponent) {
    UIUtil.uiTraverser(component).postOrderDfsTraversal().forEach { if (it is JComponent) it.updateUI() }
    add(component)
    component.invalidate()
  }

  var leftSize = 0
  var rightSize = 0
  var topSize = 0
  var bottomSize = 0
  var minSize = 0

  var showDividerControls = false
    set(value) {
      field = value
      doLayout()
      repaint()
    }

  var dividerMouseZoneSize = 0
    set(value) {
      field = JBUIScale.scale(value)
    }

  private inner class MyFocusTraversalPolicy : LayoutFocusTraversalPolicy() {
    override fun getComponentAfter(aContainer: Container, aComponent: Component): Component {
      val comp = when {
        SwingUtilities.isDescendingFrom(aComponent, leftToolWindowsComponent) -> {
          nextVisible(leftToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        SwingUtilities.isDescendingFrom(aComponent, topToolWindowsComponent) -> {
          nextVisible(topToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        SwingUtilities.isDescendingFrom(aComponent, bottomToolWindowsComponent) -> {
          nextVisible(bottomToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        SwingUtilities.isDescendingFrom(aComponent, rightToolWindowsComponent) -> {
          nextVisible(rightToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        else -> {
          nextVisible(documentComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
      }

      if (comp === aComponent) {
        // if focus is stuck on the component let it go further
        return super.getComponentAfter(aContainer, aComponent)
      }

      return comp
    }

    override fun getComponentBefore(aContainer: Container, aComponent: Component): Component {
      val comp = when {
        SwingUtilities.isDescendingFrom(aComponent, leftToolWindowsComponent) -> {
          prevVisible(leftToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        SwingUtilities.isDescendingFrom(aComponent, bottomToolWindowsComponent) -> {
          prevVisible(bottomToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        SwingUtilities.isDescendingFrom(aComponent, topToolWindowsComponent) -> {
          prevVisible(topToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        SwingUtilities.isDescendingFrom(aComponent, rightToolWindowsComponent) -> {
          prevVisible(rightToolWindowsComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
        else -> {
          prevVisible(documentComponent)?.let { findChildToFocus(it) } ?: aComponent
        }
      }

      if (comp === aComponent) {
        // if focus is stuck on the component let it go further
        return super.getComponentBefore(aContainer, aComponent)
      }

      return comp
    }

    private fun nextVisible(comp: Component?): Component? {
      @Suppress("DuplicatedCode")
      return when {
        comp === documentComponent -> when {
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          isTopToolWindowsVisible -> topToolWindowsComponent
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          isRightToolWindowsVisible -> rightToolWindowsComponent
          else -> null
        }
        comp === leftToolWindowsComponent -> when {
          isTopToolWindowsVisible -> topToolWindowsComponent
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          isRightToolWindowsVisible -> rightToolWindowsComponent
          isDocumentVisible -> documentComponent
          else -> null
        }
        comp === topToolWindowsComponent -> when {
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          isRightToolWindowsVisible -> rightToolWindowsComponent
          isDocumentVisible -> documentComponent
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          else -> null
        }
        comp === bottomToolWindowsComponent -> when {
          isRightToolWindowsVisible -> rightToolWindowsComponent
          isDocumentVisible -> documentComponent
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          isTopToolWindowsVisible -> topToolWindowsComponent
          else -> null
        }
        comp === rightToolWindowsComponent -> when {
          isDocumentVisible -> documentComponent
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          isTopToolWindowsVisible -> topToolWindowsComponent
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          else -> null
        }
        else -> null
      }
    }

    private fun prevVisible(comp: Component?): Component? {
      @Suppress("DuplicatedCode")
      return when {
        comp === documentComponent -> when {
          isRightToolWindowsVisible -> rightToolWindowsComponent
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          isTopToolWindowsVisible -> topToolWindowsComponent
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          else -> null
        }
        comp === leftToolWindowsComponent -> when {
          isDocumentVisible -> documentComponent
          isRightToolWindowsVisible -> rightToolWindowsComponent
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          isTopToolWindowsVisible -> topToolWindowsComponent
          else -> null
        }
        comp === topToolWindowsComponent -> when {
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          isDocumentVisible -> documentComponent
          isRightToolWindowsVisible -> rightToolWindowsComponent
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          else -> null
        }
        comp === bottomToolWindowsComponent -> when {
          isTopToolWindowsVisible -> topToolWindowsComponent
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          isDocumentVisible -> documentComponent
          isRightToolWindowsVisible -> rightToolWindowsComponent
          else -> null
        }
        comp === rightToolWindowsComponent -> when {
          isBottomToolWindowsVisible -> bottomToolWindowsComponent
          isTopToolWindowsVisible -> topToolWindowsComponent
          isLeftToolWindowsVisible -> leftToolWindowsComponent
          isDocumentVisible -> documentComponent
          else -> null
        }
        else -> null
      }
    }

    override fun getFirstComponent(aContainer: Container?): Component? {
      return if (isDocumentVisible) {
        findChildToFocus(documentComponent)
      } else {
        nextVisible(documentComponent)?.let { findChildToFocus(it) }
      }
    }

    override fun getLastComponent(aContainer: Container?): Component? {
      return if (isRightToolWindowsVisible) {
        findChildToFocus(rightToolWindowsComponent)
      } else {
        prevVisible(rightToolWindowsComponent)?.let { findChildToFocus(it) }
      }
    }

    private var reentrantLock = false
    override fun getDefaultComponent(aContainer: Container?): Component? {
      if (reentrantLock) return null
      reentrantLock = true

      val defaultComponent = if (isDocumentVisible) {
        findChildToFocus(documentComponent)
      } else {
        nextVisible(rightToolWindowsComponent)?.let { findChildToFocus(it) }
      }

      reentrantLock = false
      return defaultComponent
    }

    fun findChildToFocus(component: Component?): Component? {
      val ancestor = SwingUtilities.getWindowAncestor(this@ToolWindowsSplitter)
      // Step 1 : We should take into account cases with detached tool windows and documents
      //       - find the recent focus owner for the window of the splitter and
      //         make sure that the most recently focused component is inside the
      //         passed component. By the way, the recent focused component is supposed to be focusable
      val mostRecentFocusOwner = FocusUtil.getMostRecentComponent(component, ancestor)
      if (mostRecentFocusOwner != null) return mostRecentFocusOwner

      // Step 2 : If the best candidate to focus is a panel, usually it does not
      //          have focus representation for showing the focused state
      //          Let's ask the focus traversal policy what is the best candidate
      val defaultComponentInPanel = FocusUtil.getDefaultComponentInPanel(component)
      if (defaultComponentInPanel != null) return defaultComponentInPanel

      //Step 3 : Return the component, but find the first focusable component first
      return FocusUtil.findFocusableComponentIn(component as JComponent?, null)
    }
  }

  private fun getSizeForComponent(component: JComponent?): Int {
    return when {
      component === leftToolWindowsComponent -> leftSize
      component === rightToolWindowsComponent -> rightSize
      component === topToolWindowsComponent -> topSize
      component === bottomToolWindowsComponent -> bottomSize
      else -> -1
    }
  }

  fun getMaxSize(component: Component?, height: Boolean): Int {
    return if (height) {
      this.height - (getSizeForComponent(component as? JComponent)) - minSize
    } else {
      this.width - (getSizeForComponent(component as? JComponent)) - minSize
    }
  }

  fun getMinSize(component: Component?, height: Boolean): Int {
    return if (height) {
      this.getMinHeight(component as? JComponent)
    } else {
      this.getMinWidth(component as? JComponent)
    }
  }

  private fun getMinWidth(component: JComponent?): Int {
    return if (honorMinimumSize && component?.isVisible == true) {
      component.minimumSize.width
    } else {
      minSize
    }
  }

  private fun getMinHeight(component: JComponent?): Int {
    return if (honorMinimumSize && component?.isVisible == true) {
      component.minimumSize.height
    } else {
      minSize
    }
  }

  fun addDividerResizeListener(listener: ComponentListener) {
    dividerDispatcher.addListener(listener)
  }

  private enum class Anchor {
    LEFT, RIGHT, TOP, BOTTOM;

    val isVertical
      get() = this == TOP || this == BOTTOM
  }

  private inner class Divider(
    private val isOnePixel: Boolean,
    private val parentDisposable: Disposable,
    private val location: Anchor,
  ) : JPanel(GridBagLayout()) {
    private var dragging = false
    private var wasPressedOnMe = false
    private var point: Point? = null

    private var glassPane: IdeGlassPane? = null
    private var glassPaneDisposable: Disposable? = null

    private val resizeCursor = Cursor.getPredefinedCursor(when (location) {
      Anchor.LEFT -> Cursor.E_RESIZE_CURSOR
      Anchor.RIGHT -> Cursor.W_RESIZE_CURSOR
      Anchor.TOP -> Cursor.S_RESIZE_CURSOR
      Anchor.BOTTOM -> Cursor.N_RESIZE_CURSOR
    })

    private inner class MyMouseAdapter: MouseAdapter(), Weighted {
      override fun mousePressed(e: MouseEvent?) = processMouseEvent(e, this@Divider::processMouseEvent)
      override fun mouseReleased(e: MouseEvent?) = processMouseEvent(e, this@Divider::processMouseEvent)
      override fun mouseDragged(e: MouseEvent?) = processMouseEvent(e, this@Divider::processMouseMotionEvent)
      override fun mouseMoved(e: MouseEvent?) = processMouseEvent(e, this@Divider::processMouseMotionEvent)

      override fun getWeight(): Double = 1.0

      private fun processMouseEvent(e: MouseEvent?, handler: (MouseEvent?) -> Unit) {
        val event = this@Divider.getTargetEvent(e)
        handler(event)
        if (event?.isConsumed == true) {
          e?.consume()
        }
      }
    }

    private val myListener: MouseAdapter = this.MyMouseAdapter()

    private fun getTargetEvent(e: MouseEvent?): MouseEvent? {
      return SwingUtilities.convertMouseEvent(e?.component, e, this)
    }

    init {
      isFocusable = false
      enableEvents(AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
      Disposer.register(parentDisposable, UiNotifyConnector(this, object : Activatable {
        override fun showNotify() = initGlassPane(parentDisposable)
        override fun hideNotify() = releaseGlassPane()
      }))
    }

    private fun isInside(p: Point): Boolean {
      if (!isVisible) return false

      ComponentUtil.getWindow(this)?.let { window ->
        val deepestComponent = SwingUtilities.convertPoint(this, p, window).let { point ->
          UIUtil.getDeepestComponentAt(window, point.x, point.y)
        }

        val components: List<Component?> = listOf(
          leftToolWindowsComponent,
          rightToolWindowsComponent,
          bottomToolWindowsComponent,
          documentComponent,
          leftDivider,
          rightDivider,
          bottomDivider
        )

        if (ComponentUtil.findParentByCondition(deepestComponent) { it != null && components.contains(it) } == null) return false
      }

      val dndOff = if (isOnePixel) JBUIScale.scale(Registry.intValue("ide.splitter.mouseZone")) / 2 else 0

      val pPar: Int
      val pPerp: Int
      val divLength: Int
      val divThickness: Int

      if (location.isVertical) {
        pPar = p.x
        pPerp = p.y
        divLength = width
        divThickness = height
      } else {
        pPar = p.y
        pPerp = p.x
        divLength = height
        divThickness = width
      }

      return when {
        pPar < 0 || pPar >= divLength -> false
        divThickness > 0              -> pPerp >= -dndOff && pPerp < divThickness + dndOff
        else                          -> pPerp >= -dividerMouseZoneSize / 2 && pPerp <= dividerMouseZoneSize / 2
      }
    }

    private fun initGlassPane(parentDisposable: Disposable) {
      val glassPane = IdeGlassPaneUtil.find(this)
      if (glassPane === this.glassPane) return

      this.releaseGlassPane()
      if (Disposer.isDisposed(parentDisposable)) return

      val glassPaneDisposable = Disposer.newDisposable()
      Disposer.register(parentDisposable, glassPaneDisposable)
      this.glassPaneDisposable = glassPaneDisposable

      glassPane.addMousePreprocessor(myListener, glassPaneDisposable)
      glassPane.addMouseMotionPreprocessor(myListener, glassPaneDisposable)
      this.glassPane = glassPane
    }

    private fun releaseGlassPane() {
      glassPaneDisposable?.let {
        Disposer.dispose(it)
        glassPaneDisposable = null
        glassPane = null
      }
    }

    private fun center() {
      documentComponent?.let {
        val newWidth = (leftSize + it.width) / 2
        val newHeight = (topSize + it.height) / 2
        when (location) {
          Anchor.LEFT -> leftSize = newWidth
          Anchor.RIGHT -> rightSize = newWidth
          Anchor.TOP -> topSize = newHeight
          Anchor.BOTTOM -> bottomSize = newHeight
        }
      }
    }

    override fun processMouseEvent(e: MouseEvent?) {
      super.processMouseEvent(e)

      if (!this.isShowing) return

      when (e?.id) {
        MouseEvent.MOUSE_ENTERED -> cursor = resizeCursor
        MouseEvent.MOUSE_EXITED -> if (!dragging) cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
        MouseEvent.MOUSE_PRESSED -> {
          wasPressedOnMe = isInside(e.point)
          if (wasPressedOnMe) {
            glassPane?.setCursor(resizeCursor, myListener)
            e.consume()
          }
        }
        MouseEvent.MOUSE_RELEASED -> {
          if (wasPressedOnMe) e.consume()

          if (isInside(e.point)) glassPane?.setCursor(resizeCursor, myListener)

          if (dragging && dividerDispatcherDelegate.isInitialized()) {
            dividerDispatcher.multicaster.componentResized(ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED))
          }

          wasPressedOnMe = false
          dragging = false
          point = null
        }
        MouseEvent.MOUSE_CLICKED -> if (e.clickCount == 2) center()
      }
    }

    override fun processMouseMotionEvent(e: MouseEvent?) {
      super.processMouseMotionEvent(e)

      if (!isShowing || e == null) return

      when (e.id) {
        MouseEvent.MOUSE_DRAGGED -> run {
          if (!wasPressedOnMe) return@run

          dragging = true
          cursor = resizeCursor
          glassPane?.setCursor(resizeCursor, myListener)

          val mousePos = SwingUtilities.convertPoint(this, e.point, this@ToolWindowsSplitter)
          point = mousePos

          val idealSize: Int
          val component: JComponent?
          val oppositeSize: Int

          when (location) {
            Anchor.LEFT -> {
              idealSize = mousePos.x
              component = leftToolWindowsComponent
              oppositeSize = rightSize
            }
            Anchor.RIGHT -> {
              idealSize = this@ToolWindowsSplitter.width - mousePos.x - dividerWidth
              component = rightToolWindowsComponent
              oppositeSize = leftSize
            }
            Anchor.TOP -> {
              idealSize = mousePos.y
              component = topToolWindowsComponent
              oppositeSize = bottomSize
            }
            Anchor.BOTTOM -> {
              idealSize = this@ToolWindowsSplitter.height - mousePos.y - dividerWidth
              component = bottomToolWindowsComponent
              oppositeSize = topSize
            }
          }

          val minSize: Int
          val maxSize: Int

          if (location.isVertical) {
            minSize = getMinHeight(component)
            maxSize = this@ToolWindowsSplitter.height - oppositeSize - getMinHeight(
              documentComponent) - dividerWidth * visibleHorizontalDividerCount
          }
          else {
            minSize = getMinWidth(component)
            maxSize = this@ToolWindowsSplitter.width - oppositeSize - getMinWidth(
              documentComponent) - dividerWidth * visibleVerticalDividerCount
          }

          val clampedSize = if (minSize <= maxSize) idealSize.coerceIn(minSize, maxSize) else idealSize

          when (location) {
            Anchor.LEFT -> leftSize = clampedSize
            Anchor.RIGHT -> rightSize = clampedSize
            Anchor.TOP -> topSize = clampedSize
            Anchor.BOTTOM -> bottomSize = clampedSize
          }

          this@ToolWindowsSplitter.doLayout()
        }
        MouseEvent.MOUSE_MOVED -> {
          glassPane?.setCursor(if (isInside(e.point)) {
            e.consume()
            resizeCursor
          } else {
            null
          }, myListener)
        }
      }

      if (wasPressedOnMe) e.consume()
    }
  }

  data class ToolWindowsLayout(var leftByTop: Boolean = false,
                               var rightByTop: Boolean = false,
                               var leftByBottom: Boolean = false,
                               var rightByBottom: Boolean = false) {
    constructor(uiSettings: UISettings): this(
      uiSettings.toolWindowsLeftByTop,
      uiSettings.toolWindowsRightByTop,
      uiSettings.toolWindowsLeftByBottom,
      uiSettings.toolWindowsRightByBottom
    )

    fun update(uiSettings: UISettings): Boolean {
      var didUpdate = false

      if (uiSettings.toolWindowsLeftByTop != leftByTop) {
        leftByTop = uiSettings.toolWindowsLeftByTop
        didUpdate = true
      }

      if (uiSettings.toolWindowsRightByTop != rightByTop) {
        rightByTop = uiSettings.toolWindowsRightByTop
        didUpdate = true
      }

      if (uiSettings.toolWindowsLeftByBottom != leftByBottom) {
        leftByBottom = uiSettings.toolWindowsLeftByBottom
        didUpdate = true
      }

      if (uiSettings.toolWindowsRightByBottom != rightByBottom) {
        rightByBottom = uiSettings.toolWindowsRightByBottom
        didUpdate = true
      }

      return didUpdate
    }
  }

  init {
    leftDivider = Divider(onePixelDividers, parentDisposable ?: this, Anchor.LEFT)
    rightDivider = Divider(onePixelDividers, parentDisposable ?: this, Anchor.RIGHT)
    topDivider = Divider(onePixelDividers, parentDisposable ?: this, Anchor.TOP)
    bottomDivider = Divider(onePixelDividers, parentDisposable ?: this, Anchor.BOTTOM)

    if (onePixelDividers) {
      val bg = UIUtil.CONTRAST_BORDER_COLOR
      leftDivider.background = bg
      rightDivider.background = bg
      topDivider.background = bg
      bottomDivider.background = bg
    }

    isFocusCycleRoot = true
    focusTraversalPolicy = MyFocusTraversalPolicy()
    isOpaque = false

    add(leftDivider)
    add(rightDivider)
    add(topDivider)
    add(bottomDivider)
  }

  constructor(toolWindowsLayout: ToolWindowsLayout, parentDisposable: Disposable): this(toolWindowsLayout, parentDisposable, false)

  override fun isVisible(): Boolean =
    super.isVisible() && (isLeftToolWindowsVisible || isRightToolWindowsVisible || isTopToolWindowsVisible || isBottomToolWindowsVisible || isDocumentVisible)

  private fun isComponentVisible(component: JComponent?) = !NullableComponent.Check.isNull(component) && component?.isVisible ?: false

  private val isLeftToolWindowsVisible
    get() = isComponentVisible(leftToolWindowsComponent)

  private val isRightToolWindowsVisible
    get() = isComponentVisible(rightToolWindowsComponent)

  private val isTopToolWindowsVisible
    get() = isComponentVisible(topToolWindowsComponent)

  private val isBottomToolWindowsVisible
    get() = isComponentVisible(bottomToolWindowsComponent)

  private val isDocumentVisible
    get() = isComponentVisible(documentComponent)

  private val isLeftDividerVisible
    get() = isLeftToolWindowsVisible && (isDocumentVisible || isRightToolWindowsVisible && !isDocumentVisible)

  private val isRightDividerVisible
    get() = isRightToolWindowsVisible && isDocumentVisible

  private val isTopDividerVisible
    get() = isTopToolWindowsVisible && (isDocumentVisible || isBottomToolWindowsVisible && !isDocumentVisible)

  private val isBottomDividerVisible
    get() = isBottomToolWindowsVisible && isDocumentVisible

  private val visibleVerticalDividerCount
    get() = booleanArrayOf(isLeftDividerVisible, isRightDividerVisible).count { it }

  private val visibleHorizontalDividerCount
    get() = booleanArrayOf(isTopDividerVisible, isBottomDividerVisible).count { it }

  override fun getMinimumSize(): Dimension {
    if (!honorMinimumSize) return super.getMinimumSize()

    val dividerWidth = dividerWidth

    val leftSize = leftToolWindowsComponent?.minimumSize ?: JBUI.emptySize()
    val rightSize = rightToolWindowsComponent?.minimumSize ?: JBUI.emptySize()
    val topSize = topToolWindowsComponent?.minimumSize ?: JBUI.emptySize()
    val bottomSize = bottomToolWindowsComponent?.minimumSize ?: JBUI.emptySize()
    val documentSize = documentComponent?.minimumSize ?: JBUI.emptySize()

    val leftWidthPlusDivider = leftSize.width + if (isLeftDividerVisible) dividerWidth else 0
    val rightWidthPlusDivider = rightSize.width + if (isRightDividerVisible) dividerWidth else 0
    val topHeightPlusDivider = topSize.height + if (isTopDividerVisible) dividerWidth else 0
    val bottomHeightPlusDivider = bottomSize.height + if (isBottomDividerVisible) dividerWidth else 0

    var topFullWidth = topSize.width
    var bottomFullWidth = bottomSize.width
    var leftFullHeight = leftSize.height
    var rightFullHeight = rightSize.height

    when (toolWindowsLayout.leftByTop) {
      true -> topFullWidth += leftWidthPlusDivider
      false -> leftFullHeight += topHeightPlusDivider
    }

    when (toolWindowsLayout.rightByTop) {
      true -> topFullWidth += rightWidthPlusDivider
      else -> rightFullHeight += topHeightPlusDivider
    }

    when (toolWindowsLayout.leftByBottom) {
      true -> bottomFullWidth += leftWidthPlusDivider
      false -> leftFullHeight += bottomHeightPlusDivider
    }

    when (toolWindowsLayout.rightByBottom) {
      true -> bottomFullWidth += rightWidthPlusDivider
      false -> rightFullHeight += bottomHeightPlusDivider
    }

    val documentFullWidth = documentSize.width + leftWidthPlusDivider + rightWidthPlusDivider
    val documentFullHeight = documentSize.height + topHeightPlusDivider + bottomHeightPlusDivider

    return Dimension(maxOf(topFullWidth, bottomFullWidth, documentFullWidth), maxOf(leftFullHeight, rightFullHeight, documentFullHeight))
  }

  override fun doLayout() {
    val width = width
    val height = height

    val dividerWidthX: Int
    val dividerWidthY: Int
    val dividerCountX = visibleVerticalDividerCount
    val dividerCountY = visibleHorizontalDividerCount

    var leftWidth: Int
    var documentWidth: Int
    var rightWidth: Int

    var topHeight: Int
    var documentHeight: Int
    var bottomHeight: Int

    val minDocumentWidth = getMinWidth(documentComponent)
    val minDocumentHeight = getMinHeight(documentComponent)

    if (width <= dividerCountX * dividerWidth) {
      leftWidth = 0
      documentWidth = 0
      rightWidth = 0
      dividerWidthX = width
    } else {
      leftWidth = leftSize
      rightWidth = rightSize
      dividerWidthX = dividerWidth

      val widthLack = leftWidth + rightWidth - (width - dividerCountX * dividerWidthX - minSize)
      if (widthLack > 0) {
        val leftWidthRatio = leftWidth.toDouble() / (leftWidth + rightWidth)
        if (leftWidth > 0) leftWidth = (leftWidth - (widthLack * leftWidthRatio).toInt()).coerceAtLeast(minSize)
        if (rightWidth > 0) rightWidth = (rightWidth - (widthLack * (1 - leftWidthRatio)).toInt()).coerceAtLeast(minSize)
        documentWidth = minDocumentWidth
      } else {
        documentWidth = (width - dividerCountX * dividerWidthX - leftSize - rightSize).coerceAtLeast(minDocumentWidth)
      }

      if (!isDocumentVisible) {
        if (!isRightToolWindowsVisible) {
          leftWidth = width
        } else {
          rightWidth += documentWidth
        }

        documentWidth = 0
      }
    }

    if (height <= dividerCountY * dividerWidth) {
      topHeight = 0
      documentHeight = 0
      bottomHeight = 0
      dividerWidthY = height
    } else {
      topHeight = topSize
      bottomHeight = bottomSize
      dividerWidthY = dividerWidth

      val heightLack = topHeight + bottomHeight - (height - dividerCountY * dividerWidthY - minSize)
      if (heightLack > 0) {
        val topHeightRatio = topHeight.toDouble() / (topHeight + bottomHeight)
        if (topHeight > 0) topHeight = (topHeight - (heightLack * topHeightRatio).toInt()).coerceAtLeast(minSize)
        if (bottomHeight > 0) bottomHeight = (bottomHeight - (heightLack * (1 - topHeightRatio)).toInt()).coerceAtLeast(minSize)
        documentHeight = minDocumentHeight
      } else {
        documentHeight = (height - dividerCountY * dividerWidthY - topSize - bottomSize).coerceAtLeast(minDocumentHeight)
      }

      if (!isDocumentVisible) {
        if (!isBottomToolWindowsVisible) {
          topHeight = height
        } else {
          bottomHeight += documentHeight
        }

        documentHeight = 0
      }
    }

    val leftDividerWidth = if (isLeftDividerVisible) dividerWidthX else 0
    val rightDividerWidth = if (isRightDividerVisible) dividerWidthX else 0
    val topDividerHeight = if (isTopDividerVisible) dividerWidthY else 0
    val bottomDividerHeight = if (isBottomDividerVisible) dividerWidthY else 0

    val leftX = 0
    val leftDividerX = leftX + leftWidth
    val documentX = leftDividerX + leftDividerWidth
    val rightDividerX = documentX + documentWidth
    val rightX = rightDividerX + rightDividerWidth

    val topY = 0
    val topDividerY = topY + topHeight
    val documentY = topDividerY + topDividerHeight
    val bottomDividerY = documentY + documentHeight
    val bottomY = bottomDividerY + bottomDividerHeight

    var topWidth = documentWidth
    var bottomWidth = documentWidth
    var leftHeight = documentHeight
    var rightHeight = documentHeight

    when (toolWindowsLayout.leftByTop) {
      true -> leftHeight += topHeight + topDividerHeight
      false -> topWidth += leftWidth + leftDividerWidth
    }

    when (toolWindowsLayout.rightByTop) {
      true -> rightHeight += topHeight + topDividerHeight
      false -> topWidth += rightWidth + rightDividerWidth
    }

    when (toolWindowsLayout.leftByBottom) {
      true -> leftHeight += bottomHeight + bottomDividerHeight
      false -> bottomWidth += leftWidth + leftDividerWidth
    }

    when (toolWindowsLayout.rightByBottom) {
      true -> rightHeight += bottomHeight + bottomDividerHeight
      false -> bottomWidth += rightWidth + rightDividerWidth
    }

    val topX = if (toolWindowsLayout.leftByTop) documentX else 0
    val bottomX = if (toolWindowsLayout.leftByBottom) documentX else 0
    val leftY = if (toolWindowsLayout.leftByTop) 0 else documentY
    val rightY = if (toolWindowsLayout.rightByTop) 0 else documentY

    fun layoutDivider(divider: Divider, visible: Boolean, rect: Rectangle) {
      divider.isVisible = visible
      if (visible) divider.bounds = rect
      divider.doLayout()
    }

    layoutDivider(leftDivider, isLeftDividerVisible, Rectangle(leftDividerX, leftY, leftDividerWidth, leftHeight))
    layoutDivider(rightDivider, isRightDividerVisible, Rectangle(rightDividerX, rightY, rightDividerWidth, rightHeight))
    layoutDivider(topDivider, isTopDividerVisible, Rectangle(topX, topDividerY, topWidth, topDividerHeight))
    layoutDivider(bottomDivider, isBottomDividerVisible, Rectangle(bottomX, bottomDividerY, bottomWidth, bottomDividerHeight))

    fun validate(component: JComponent?, rect: Rectangle) {
      if (Splitter.isNull(component) || component == null) {
        Splitter.hideNull(component)
        return
      }

      if (component.bounds != rect) {
        component.bounds = rect
        component.revalidate()
      }
    }

    validate(leftToolWindowsComponent, Rectangle(leftX, leftY, leftWidth, leftHeight))
    validate(rightToolWindowsComponent, Rectangle(rightX, rightY, rightWidth, rightHeight))
    validate(topToolWindowsComponent, Rectangle(topX, topY, topWidth, topHeight))
    validate(bottomToolWindowsComponent, Rectangle(bottomX, bottomY, bottomWidth, bottomHeight))
    validate(documentComponent, Rectangle(documentX, documentY, documentWidth, documentHeight))
  }

  override fun dispose() {}
}