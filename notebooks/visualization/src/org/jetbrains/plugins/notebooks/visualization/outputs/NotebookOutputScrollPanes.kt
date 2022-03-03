package org.jetbrains.plugins.notebooks.visualization.outputs

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ComponentUtil
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.MouseEventHandler
import java.awt.Component
import java.awt.Insets
import java.awt.Point
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane

internal fun getEditorBackground() = EditorColorsManager.getInstance().globalScheme.defaultBackground

/** Default output scroll pane similar to one used in the IDEA editor features no border and corners
 * that respect content background. */
open class NotebookOutputDefaultScrollPane(view: Component, private val validateRoot: Boolean = true) : JBScrollPane(view) {
  private val view get() = viewport.view

  init {
    border = IdeBorderFactory.createEmptyBorder(Insets(0, 0, 0, 0))
  }

  override fun updateUI() {
    super.updateUI()
    setupScrollBars()
    setupCorners()
  }

  override fun isValidateRoot(): Boolean = validateRoot

  private fun setupScrollBars() {
    setScrollBar(verticalScrollBar)
    setScrollBar(horizontalScrollBar)
  }

  protected open fun setScrollBar(scrollBar: JScrollBar) {
    scrollBar.apply {
      isOpaque = true
      background = getEditorBackground()
    }
  }

  override fun setupCorners() {
    setCorner(LOWER_RIGHT_CORNER, Corner())
    setCorner(UPPER_RIGHT_CORNER, Corner())
  }

  private class Corner : JPanel() {
    init {
      background = getEditorBackground()
    }
  }
}

/** A scroll pane that doesn't capture cursor immediately.
 *
 * The main differences of this scroll pane implementation from [JBScrollPane] are:
 *
 * 1. It always shows the scroll bars.
 * 2. It doesn't start scrolling immediately after getting a mouse wheel event thus does not interfere with the editor scrolling.
 * 3. Mouse click or move inside the scroll pane makes it to handle further mouse wheel events unconditionally.
 * 4. When top or bottom of the scroll pane is reached, it doesn't continue scrolling the outer scroll pane immediately.
 *
 * */
open class NotebookOutputNonStickyScrollPane(
  view: Component,
  validateRoot: Boolean = true,
) : NotebookOutputDefaultScrollPane(view, validateRoot) {
  private var latestMouseWheelEventTime = 0L
  private var mouseEnteredTime = 0L

  private var reachedTopOrBottomTime = 0L

  /** Counting consequential rotation events in the same direction is a hack necessary for more
   * reliable detection of a scrolling direction than using [MouseWheelEvent.wheelRotation]. The latter property
   * often returns misleading values, f.e. occasional negative values when scrolling down. To bypass this,
   * we require [consequentialScrollsSensitivity] consequential events in a direction
   * to detect we are really scrolling there. */
  private var consequentialUpRotationEvents = 0
  private var consequentialDownRotationEvents = 0
  private val consequentialScrollsSensitivity = 2

  private val threshold = Registry.get("python.ds.jupyter.scrolling.innerScrollCooldownTime").asInteger().toLong()

  /** If true, the scroll pane should handle the mouse wheel event unconditionally. */
  private var isScrollCaptured = false

  private val mouseAdapter = MyMouseHandler()
  private val containerAdapter = MyContainerAdapter()

  init {
    recursivelyAddMouseListenerToComponent(this, mouseAdapter)
    recursivelyAddMouseMotionListenerToComponent(this, mouseAdapter)
    recursivelyAddContainerListenerToComponent(this, containerAdapter)
  }

  override fun processMouseWheelEvent(e: MouseWheelEvent) {
    rememberRotationDirection(e)
    val eventTime = e.`when`
    when {
      isScrollCaptured -> {
        handleScrollEventTopBottomAware(e)
      }
      eventTime - mouseEnteredTime < threshold || eventTime - latestMouseWheelEventTime < threshold -> {
        latestMouseWheelEventTime = eventTime
        delegateToParentScrollPane(e)
      }
      else -> {
        isScrollCaptured = true
        handleScrollEventTopBottomAware(e)
      }
    }
  }

  /** Handles a scroll event in scroll pane borders aware manner, f.e. when the top of the scroll pane
   * is reached, scrolling stops and does not immediately continue in the outer scroll pane. This protects
   * from occasional "dropping out" of a component that currently in use. */
  private fun handleScrollEventTopBottomAware(e: MouseWheelEvent) {
    if (hasReachedTop() || hasReachedBottom()) {
      if (reachedTopOrBottomTime == 0L) {
        reachedTopOrBottomTime = e.`when`
      }
      else {
        val delta = e.`when` - reachedTopOrBottomTime
        if (delta > threshold / 10) {
          super.processMouseWheelEvent(e)
        }
        else {
          reachedTopOrBottomTime = e.`when`
        }
      }
    }
    else {
      reachedTopOrBottomTime = 0L
      super.processMouseWheelEvent(e)
    }
  }

  private fun hasReachedTop(): Boolean {
    if (consequentialDownRotationEvents > consequentialScrollsSensitivity) return false
    verticalScrollBar ?: return false
    return verticalScrollBar.value == verticalScrollBar.minimum
  }

  private fun hasReachedBottom(): Boolean {
    if (consequentialUpRotationEvents > consequentialScrollsSensitivity) return false
    verticalScrollBar ?: return false
    return verticalScrollBar.value == verticalScrollBar.maximum - verticalScrollBar.model.extent
  }

  private fun rememberRotationDirection(e: MouseWheelEvent) {
    if (isUpRotation(e)) {
      consequentialDownRotationEvents = 0
      consequentialUpRotationEvents++
    }
    else if (isDownRotation(e)) {
      consequentialUpRotationEvents = 0
      consequentialDownRotationEvents++
    }
  }

  private fun isUpRotation(e: MouseWheelEvent) = e.wheelRotation < 0

  private fun isDownRotation(e: MouseWheelEvent) = e.wheelRotation > 0

  private fun delegateToParentScrollPane(e: MouseEvent) {
    val parentScrollPane: JScrollPane? = findParentOfType(JScrollPane::class.java)
    if (parentScrollPane != null) {
      e.source = parentScrollPane
      parentScrollPane.dispatchEvent(e)
    }
  }

  private fun <T> findParentOfType(type: Class<T>): T? {
    parent ?: return null
    return ComponentUtil.getParentOfType(type, parent)
  }

  inner class MyMouseHandler : MouseEventHandler() {
    override fun mouseEntered(e: MouseEvent) {
      if (mouseEnteredTime == 0L) {
        mouseEnteredTime = e.`when`
        latestMouseWheelEventTime = 0
      }
      super.mouseEntered(e)
    }

    override fun mouseExited(e: MouseEvent) {
      if (!isShowing) {
        // In some cases, e.g. for concatenated outputs, a component may not be shown,
        // so it is necessary to find a visible upper level scroll pane component and delegate
        // event processing to it.
        delegateToParentScrollPane(e)
      }
      else {
        val eventPointOnScreen = Point(e.xOnScreen, e.yOnScreen)
        val xRange = locationOnScreen.x..locationOnScreen.x + width
        val yRange = locationOnScreen.y..locationOnScreen.y + height
        if (eventPointOnScreen.x !in xRange || eventPointOnScreen.y !in yRange) {
          mouseEnteredTime = 0
          latestMouseWheelEventTime = 0
          isScrollCaptured = false
        }
      }
      super.mouseExited(e)
    }

    override fun mousePressed(e: MouseEvent) {
      isScrollCaptured = true
      super.mousePressed(e)
    }

    override fun mouseMoved(e: MouseEvent?) {
      if (!contentFitsViewport()) {
        // DS-1193: If the viewport fits the entire view, there is no reason to capture
        // the scroll. An unwanted additional attempt is needed to scroll the editor otherwise.
        isScrollCaptured = true
      }
      super.mouseMoved(e)
    }

    override fun handle(e: MouseEvent) {
      // The mouse listener is attached to every component inside the scroll pane
      // to track mouse events and make the scrolling work the way we need. It can lead
      // to the situation when components not intended to receive mouse events receive
      // them. This makes the events invisible to the underlying parent components.
      // The code below ensures that events will reach parents that may be interested in them.
      e.takeUnless { it.isConsumed }?.source?.castSafelyTo<Component>()?.parent?.let {
        it.dispatchEvent(MouseEvent(
          it, e.id, e.`when`, e.modifiersEx, e.x, e.y, e.xOnScreen, e.yOnScreen, e.clickCount, e.isPopupTrigger, e.button,
        ))
      }
    }

    private fun contentFitsViewport(): Boolean {
      val viewRect = viewport.viewRect
      return viewRect.x == 0
             && viewRect.y == 0
             && viewRect.height == viewport.view.height
             && viewRect.width == viewport.view.width
    }
  }

  inner class MyContainerAdapter : ContainerAdapter() {
    override fun componentAdded(e: ContainerEvent) {
      (e.child as? JComponent)?.let {
        recursivelyAddMouseListenerToComponent(it, mouseAdapter)
        recursivelyAddMouseMotionListenerToComponent(it, mouseAdapter)
      }
    }

    override fun componentRemoved(e: ContainerEvent) {
      (e.child as? JComponent)?.let {
        recursivelyRemoveListeners(it)
      }
    }
  }
}

private fun recursivelyAddMouseListenerToComponent(comp: JComponent, listener: MouseListener) {
  comp.addMouseListener(listener)
  for (c in comp.components) {
    if (c is JComponent) {
      recursivelyAddMouseListenerToComponent(c, listener)
    }
  }
}

private fun recursivelyAddMouseMotionListenerToComponent(comp: JComponent, listener: MouseMotionListener) {
  comp.addMouseMotionListener(listener)
  for (c in comp.components) {
    if (c is JComponent) {
      recursivelyAddMouseMotionListenerToComponent(c, listener)
    }
  }
}

private fun recursivelyAddContainerListenerToComponent(comp: JComponent, listener: ContainerListener) {
  comp.addContainerListener(listener)
  for (c in comp.components) {
    if (c is JComponent) {
      recursivelyAddContainerListenerToComponent(c, listener)
    }
  }
}

private fun recursivelyRemoveListeners(comp: JComponent) {
  val queue = mutableListOf<JComponent>()
  queue.add(comp)
  while (queue.isNotEmpty()) {
    val c = queue.removeAt(queue.lastIndex)
    c.containerListeners.forEach {
      if (it is NotebookOutputNonStickyScrollPane.MyContainerAdapter) {
        c.removeContainerListener(it)
      }
    }
    c.mouseListeners.forEach {
      if (it is NotebookOutputNonStickyScrollPane.MyMouseHandler) {
        c.removeMouseListener(it)
      }
    }
    c.components.filterIsInstance<JComponent>().forEach { queue.add(it) }
  }
}
