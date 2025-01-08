// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.MathUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.UIUtil.ComponentStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.plaf.ScrollBarUI
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
open class DefaultScrollBarUI @JvmOverloads internal constructor(
  private val thickness: Int = if (ScrollSettings.isThumbSmallIfOpaque.invoke()) 13 else 10,
  private val thicknessMax: Int = 14,
  private val thicknessMin: Int = 10,
) : ScrollBarUI() {
  private val listener = Listener()
  private val scrollTimer = TimerUtil.createNamedTimer("ScrollBarThumbScrollTimer", 60, listener)

  internal var installedState: DefaultScrollbarUiInstalledState? = null
    private set

  private var isValueCached: Boolean = false
  private var cachedValue: Int = 0
  private var oldValue: Int = 0

  companion object {
    @ApiStatus.Internal
    @JvmField
    val LEADING: Key<Component> = Key.create("JB_SCROLL_BAR_LEADING_COMPONENT")

    @ApiStatus.Internal
    @JvmField
    val TRAILING: Key<Component> = Key.create("JB_SCROLL_BAR_TRAILING_COMPONENT")

    fun isOpaque(c: Component): Boolean {
      if (c.isOpaque) {
        return true
      }

      val parent = c.parent
      // do not allow non-opaque scroll bars, because default layout does not support them
      return parent is JScrollPane && parent.getLayout() is ScrollPaneLayout.UIResource
    }
  }

  internal open fun createWrapAnimationBehaviour(state: DefaultScrollbarUiInstalledState): ScrollBarAnimationBehavior {
    return ToggleableScrollBarAnimationBehaviorDecorator(
      decoratedBehavior = createBaseAnimationBehavior(state),
      trackAnimator = state.track.animator,
      thumbAnimator = state.thumb.animator,
    )
  }

  internal open fun createThumbPainter(state: DefaultScrollbarUiInstalledState): ScrollBarPainter.Thumb {
    return ScrollBarPainter.Thumb({ state.scrollBar }, false, state.coroutineScope)
  }

  internal open fun createBaseAnimationBehavior(state: DefaultScrollbarUiInstalledState): ScrollBarAnimationBehavior {
    return DefaultScrollBarAnimationBehavior(trackAnimator = state.track.animator, thumbAnimator = state.thumb.animator)
  }

  private fun getEffectiveThickness(): Int {
    val scrollBar = installedState?.scrollBar
    return scale(if (scrollBar == null || isOpaque(scrollBar)) thickness else thicknessMax)
  }

  private fun getMinimalThickness(): Int {
    val scrollBar = installedState?.scrollBar
    return scale(if (scrollBar == null || isOpaque(scrollBar)) thickness else thicknessMin)
  }

  fun toggle(isOn: Boolean) {
    installedState?.animationBehavior?.onToggle(isOn)
  }

  open fun isAbsolutePositioning(event: MouseEvent): Boolean = SwingUtilities.isMiddleMouseButton(event)

  open fun isTrackClickable(): Boolean {
    val state = installedState ?: return false
    return isOpaque(state.scrollBar) || state.animationBehavior.trackFrame > 0
  }

  private fun isMousePressedOnVisiblePart(event: MouseEvent): Boolean {
    return event.id == MouseEvent.MOUSE_PRESSED && isThumbContains(event.x, event.y)
  }

  open val isTrackExpandable: Boolean
    get() = false

  fun isTrackContains(x: Int, y: Int): Boolean {
    return installedState!!.track.bounds.contains(x, y)
  }

  fun isThumbContains(x: Int, y: Int): Boolean {
    return installedState?.thumb?.bounds?.contains(x, y) == true
  }

  protected open fun paintTrack(g: Graphics2D, c: JComponent) {
    paint(p = installedState!!.track, g = g, c = c, small = false)
  }

  internal open fun paintThumb(g: Graphics2D, c: JComponent, state: DefaultScrollbarUiInstalledState) {
    paint(p = installedState!!.thumb, g = g, c = c, small = ScrollSettings.isThumbSmallIfOpaque.invoke() && isOpaque(c))
  }

  fun paint(p: ScrollBarPainter, g: Graphics2D, c: JComponent?, small: Boolean) {
    var x: Int = p.bounds.x
    var y: Int = p.bounds.y
    var width: Int = p.bounds.width
    var height: Int = p.bounds.height

    val alignment = JBScrollPane.Alignment.get(c)
    if (alignment == JBScrollPane.Alignment.LEFT || alignment == JBScrollPane.Alignment.RIGHT) {
      val offset: Int = getTrackOffset(width - getMinimalThickness())
      if (offset > 0) {
        width -= offset
        if (alignment == JBScrollPane.Alignment.RIGHT) x += offset
      }
    }
    else {
      val offset: Int = getTrackOffset(height - getMinimalThickness())
      if (offset > 0) {
        height -= offset
        if (alignment == JBScrollPane.Alignment.BOTTOM) y += offset
      }
    }

    val insets: Insets = getInsets(small)
    x += insets.left
    y += insets.top
    width -= (insets.left + insets.right)
    height -= (insets.top + insets.bottom)

    p.paint(g, x, y, width, height, p.animator.value)
  }

  protected open fun getInsets(small: Boolean): Insets {
    return if (small) JBUI.insets(1) else JBUI.emptyInsets()
  }

  private fun getTrackOffset(offset: Int): Int {
    if (!isTrackExpandable) {
      return offset
    }

    val value = installedState?.animationBehavior?.trackFrame ?: 0f
    return when {
      value <= 0 -> offset
      value >= 1 -> 0
      else -> (.5f + offset * (1 - value)).toInt()
    }
  }

  fun repaint() {
    installedState?.scrollBar?.repaint()
  }

  fun repaint(x: Int, y: Int, width: Int, height: Int) {
    installedState?.scrollBar?.repaint(x, y, width, height)
  }

  private fun scale(value: Int): Int {
    val scaledValue = JBUIScale.scale(value)
    return when (UIUtil.getComponentStyle(installedState!!.scrollBar)) {
      ComponentStyle.LARGE -> (scaledValue * 1.15).toInt()
      ComponentStyle.SMALL -> (scaledValue * 0.857).toInt()
      ComponentStyle.MINI -> (scaledValue * 0.714).toInt()
      ComponentStyle.REGULAR -> scaledValue
    }
  }

  override fun installUI(c: JComponent) {
    val scrollBar = c as JScrollBar
    installedState = DefaultScrollbarUiInstalledState(ui = this, scrollBar = scrollBar)

    ScrollBarPainter.setBackground(c)
    scrollBar.isOpaque = false
    scrollBar.isFocusable = false
    scrollBar.addMouseListener(listener)
    scrollBar.addMouseMotionListener(listener)
    scrollBar.model.addChangeListener(listener)
    scrollBar.addPropertyChangeListener(listener)
    scrollBar.addFocusListener(listener)
    scrollTimer.initialDelay = 300
  }

  override fun uninstallUI(c: JComponent) {
    val state = installedState ?: return
    state.coroutineScope.cancel()
    installedState = null

    state.animationBehavior.onUninstall()

    scrollTimer.stop()
    val scrollBar = state.scrollBar
    scrollBar.removeFocusListener(listener)
    scrollBar.removePropertyChangeListener(listener)
    scrollBar.model.removeChangeListener(listener)
    scrollBar.removeMouseMotionListener(listener)
    scrollBar.removeMouseListener(listener)
    scrollBar.foreground = null
    scrollBar.background = null
  }

  override fun getPreferredSize(c: JComponent): Dimension {
    val scrollBar = installedState!!.scrollBar
    val thickness = getEffectiveThickness()
    val alignment = JBScrollPane.Alignment.get(c)
    val preferred = Dimension(thickness, thickness)
    if (alignment == JBScrollPane.Alignment.LEFT || alignment == JBScrollPane.Alignment.RIGHT) {
      preferred.height += preferred.height
      addPreferredHeight(preferred, ClientProperty.get(scrollBar, LEADING))
      addPreferredHeight(preferred, ClientProperty.get(scrollBar, TRAILING))
    }
    else {
      preferred.width += preferred.width
      addPreferredWidth(preferred, ClientProperty.get(scrollBar, LEADING))
      addPreferredWidth(preferred, ClientProperty.get(scrollBar, TRAILING))
    }
    return preferred
  }

  override fun paint(g: Graphics, c: JComponent) {
    val alignment = JBScrollPane.Alignment.get(c)
    if (alignment == null || g !is Graphics2D) {
      return
    }

    val background: Color? = if (isOpaque(c)) c.background else null
    if (background != null) {
      g.setColor(background)
      g.fillRect(0, 0, c.width, c.height)
    }

    val bounds = Rectangle(c.width, c.height)
    JBInsets.removeFrom(bounds, c.insets)
    // process an area before the track
    val leading: Component? = ClientProperty.get(c, LEADING)
    if (leading != null) {
      if (alignment == JBScrollPane.Alignment.LEFT || alignment == JBScrollPane.Alignment.RIGHT) {
        val size = leading.preferredSize.height
        leading.setBounds(bounds.x, bounds.y, bounds.width, size)
        bounds.height -= size
        bounds.y += size
      }
      else {
        val size: Int = leading.preferredSize.width
        leading.setBounds(bounds.x, bounds.y, size, bounds.height)
        bounds.width -= size
        bounds.x += size
      }
    }

    // process an area after the track
    val trailing: Component? = ClientProperty.get(c, TRAILING)
    if (trailing != null) {
      if (alignment == JBScrollPane.Alignment.LEFT || alignment == JBScrollPane.Alignment.RIGHT) {
        val size: Int = trailing.preferredSize.height
        bounds.height -= size
        trailing.setBounds(bounds.x, bounds.y + bounds.height, bounds.width, size)
      }
      else {
        val size: Int = trailing.preferredSize.width
        bounds.width -= size
        trailing.setBounds(bounds.x + bounds.width, bounds.y, size, bounds.height)
      }
    }

    // do not set track size bigger that expected thickness
    if (alignment == JBScrollPane.Alignment.LEFT || alignment == JBScrollPane.Alignment.RIGHT) {
      val offset: Int = bounds.width - getEffectiveThickness()
      if (offset > 0) {
        bounds.width -= offset
        if (alignment == JBScrollPane.Alignment.RIGHT) bounds.x += offset
      }
    }
    else {
      val offset: Int = bounds.height - getEffectiveThickness()
      if (offset > 0) {
        bounds.height -= offset
        if (alignment == JBScrollPane.Alignment.BOTTOM) bounds.y += offset
      }
    }

    val state = installedState!!
    val track = state.track
    // animate thumb on resize
    val animate = track.bounds != bounds
    if (animate) {
      track.bounds.bounds = bounds
    }
    updateThumbBounds(animate)
    paintTrack(g, c)
    // process additional drawing on the track
    val trackPainter: RegionPainter<Any>? = ClientProperty.get(c, JBScrollBar.TRACK)
    if (trackPainter != null && track.bounds.width > 0 && track.bounds.height > 0) {
      trackPainter.paint(g, track.bounds.x, track.bounds.y, track.bounds.width, track.bounds.height, null)
    }

    // process drawing the thumb
    if (state.thumb.bounds.width > 0 && state.thumb.bounds.height > 0) {
      paintThumb(g, c, state)
    }
  }

  private fun updateThumbBounds(animate: Boolean) {
    var animate = animate
    var value = 0
    val state = installedState!!
    val scrollBar = state.scrollBar
    val min: Int = scrollBar.minimum
    val max: Int = scrollBar.maximum
    val range: Int = max - min
    if (range <= 0) {
      state.thumb.bounds.setBounds(0, 0, 0, 0)
    }
    else if (Adjustable.VERTICAL == scrollBar.orientation) {
      val extent = scrollBar.visibleAmount
      val track = state.track
      val height = max(
        convert(newRange = track.bounds.height.toDouble(), oldValue = extent.toDouble(), oldRange = range.toDouble()).toDouble(),
        (2 * getEffectiveThickness()).toDouble(),
      ).toInt()
      if (track.bounds.height <= height) {
        state.thumb.bounds.setBounds(0, 0, 0, 0)
      }
      else {
        value = this.value
        val maxY = track.bounds.y + track.bounds.height - height
        val y = if ((value < max - extent)) {
          convert(
            newRange = (track.bounds.height - height).toDouble(),
            oldValue = (value - min).toDouble(),
            oldRange = (range - extent).toDouble(),
          )
        }
        else {
          maxY
        }
        state.thumb.bounds.setBounds(track.bounds.x, adjust(y, track.bounds.y, maxY), track.bounds.width, height)
        // animate thumb on move
        animate = animate or (oldValue != value)
      }
    }
    else {
      val track = state.track
      val extent = scrollBar.visibleAmount
      val width: Int = max(
        convert(
          newRange = track.bounds.width.toDouble(),
          oldValue = extent.toDouble(),
          oldRange = range.toDouble(),
        ).toDouble(),
        (2 * getEffectiveThickness()).toDouble(),
      ).toInt()
      if (track.bounds.width <= width) {
        state.thumb.bounds.setBounds(0, 0, 0, 0)
      }
      else {
        value = this.value
        val maxX: Int = track.bounds.x + track.bounds.width - width
        var x: Int = if ((value < max - extent)) convert((track.bounds.width - width).toDouble(), (value - min).toDouble(),
                                                         (range - extent).toDouble())
        else maxX
        if (!scrollBar.componentOrientation.isLeftToRight) {
          x = track.bounds.x - x + maxX
        }
        state.thumb.bounds.setBounds(adjust(x, track.bounds.x, maxX), track.bounds.y, width, track.bounds.height)
        animate = animate or (oldValue != value) // animate thumb on move
      }
    }
    oldValue = value
    if (animate) {
      state.animationBehavior.onThumbMove()
    }
  }

  private val value: Int
    get() = if (isValueCached) cachedValue else installedState!!.scrollBar.value

  private inner class Listener : MouseAdapter(), ActionListener, FocusListener, ChangeListener, PropertyChangeListener {
    private var myOffset: Int = 0
    private var myMouseX: Int = 0
    private var myMouseY: Int = 0
    private var isReversed: Boolean = false
    private var isDragging: Boolean = false
    private var isOverTrack: Boolean = false
    private var isOverThumb: Boolean = false

    fun updateMouse(x: Int, y: Int) {
      if (isTrackContains(x, y)) {
        val animationBehavior = installedState?.animationBehavior ?: return
        if (!isOverTrack) {
          animationBehavior.onTrackHover(true.also { isOverTrack = true })
        }
        val hover: Boolean = isThumbContains(x, y)
        if (isOverThumb != hover) {
          animationBehavior.onThumbHover(hover.also { isOverThumb = it })
        }
      }
      else {
        updateMouseExit()
      }
    }

    fun updateMouseExit() {
      val animationBehavior = installedState?.animationBehavior ?: return
      if (isOverThumb) {
        animationBehavior.onThumbHover(false.also { isOverThumb = false })
      }
      if (isOverTrack) {
        animationBehavior.onTrackHover(false.also { isOverTrack = false })
      }
    }

    fun passMouseEventThroughInvisibleScrollbar(event: MouseEvent): Boolean {
      if (isTrackClickable() || isMousePressedOnVisiblePart(event)) {
        return false
      }

      // redispatch current event to the view
      val parent = installedState?.scrollBar?.parent
      val view = (parent as? JScrollPane)?.viewport?.view
      if (view != null) {
        val point = event.locationOnScreen
        SwingUtilities.convertPointFromScreen(point, view)
        MouseEventAdapter.redispatch(event, SwingUtilities.getDeepestComponentAt(view, point.x, point.y))
      }
      return true
    }

    override fun mouseClicked(e: MouseEvent) {
      val scrollBar = installedState?.scrollBar ?: return
      if (scrollBar.isEnabled) {
        passMouseEventThroughInvisibleScrollbar(e)
      }
    }

    override fun mousePressed(event: MouseEvent) {
      val scrollBar = installedState?.scrollBar ?: return
      if (!scrollBar.isEnabled || passMouseEventThroughInvisibleScrollbar(event) || SwingUtilities.isRightMouseButton(event)) {
        return
      }

      isValueCached = true
      cachedValue = scrollBar.value
      scrollBar.valueIsAdjusting = true

      myMouseX = event.x
      myMouseY = event.y

      val thumb = installedState!!.thumb

      val vertical = Adjustable.VERTICAL == scrollBar.orientation
      if (isThumbContains(myMouseX, myMouseY)) {
        // pressed on the thumb
        myOffset = if (vertical) (myMouseY - thumb.bounds.y) else (myMouseX - thumb.bounds.x)
        isDragging = true
      }
      else if (isTrackContains(myMouseX, myMouseY)) {
        // pressed on the track
        if (isAbsolutePositioning(event)) {
          myOffset = (if (vertical) thumb.bounds.height else thumb.bounds.width) / 2
          isDragging = true
          setValueFrom(event)
        }
        else {
          scrollTimer.stop()
          isDragging = false
          if (Adjustable.VERTICAL == scrollBar.orientation) {
            val y = if (thumb.bounds.isEmpty) scrollBar.height / 2 else thumb.bounds.y
            isReversed = myMouseY < y
          }
          else {
            val x = if (thumb.bounds.isEmpty) scrollBar.width / 2 else thumb.bounds.x
            isReversed = myMouseX < x
            if (!scrollBar.componentOrientation.isLeftToRight) {
              isReversed = !isReversed
            }
          }
          scroll(isReversed)
          startScrollTimerIfNecessary()
        }
      }
    }

    override fun mouseReleased(event: MouseEvent) {
      if (isDragging) {
        updateMouse(event.x, event.y)
      }

      val scrollBar = installedState?.scrollBar ?: return
      if (!scrollBar.isEnabled) {
        return
      }

      scrollBar.valueIsAdjusting = false
      if (passMouseEventThroughInvisibleScrollbar(event)) {
        return
      }
      if (SwingUtilities.isRightMouseButton(event)) {
        return
      }

      isDragging = false
      myOffset = 0
      scrollTimer.stop()
      isValueCached = true
      cachedValue = scrollBar.value
      repaint()
    }

    override fun mouseDragged(event: MouseEvent) {
      val scrollBar = installedState?.scrollBar ?: return
      if (!scrollBar.isEnabled) {
        return
      }

      if (installedState!!.thumb.bounds.isEmpty || SwingUtilities.isRightMouseButton(event)) {
        return
      }

      if (isDragging) {
        setValueFrom(event)
      }
      else {
        myMouseX = event.x
        myMouseY = event.y
        updateMouse(myMouseX, myMouseY)
        startScrollTimerIfNecessary()
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      val scrollBar = installedState?.scrollBar ?: return
      if (!scrollBar.isEnabled) {
        return
      }
      if (!isDragging) {
        updateMouse(event.x, event.y)
      }
    }

    override fun mouseExited(event: MouseEvent) {
      val scrollBar = installedState?.scrollBar ?: return
      if (!scrollBar.isEnabled) {
        return
      }
      if (!isDragging) {
        updateMouseExit()
      }
    }

    override fun actionPerformed(event: ActionEvent) {
      val scrollBar = installedState?.scrollBar
      if (scrollBar == null) {
        scrollTimer.stop()
      }
      else {
        scroll(isReversed)
        if (!installedState!!.thumb.bounds.isEmpty) {
          if (if (isReversed) !isMouseBeforeThumb() else !isMouseAfterThumb()) {
            scrollTimer.stop()
          }
        }
        val value: Int = scrollBar.value
        if (if (isReversed) value <= scrollBar.minimum else value >= scrollBar.maximum - scrollBar.visibleAmount) {
          scrollTimer.stop()
        }
      }
    }

    override fun focusGained(event: FocusEvent) {
      repaint()
    }

    override fun focusLost(event: FocusEvent) {
      repaint()
    }

    override fun stateChanged(event: ChangeEvent) {
      updateThumbBounds(false)
      // TODO: update mouse
      isValueCached = false
      repaint()
    }

    override fun propertyChange(event: PropertyChangeEvent) {
      val name: String = event.propertyName
      if ("model" == name) {
        val oldModel: BoundedRangeModel = event.oldValue as BoundedRangeModel
        val newModel: BoundedRangeModel = event.newValue as BoundedRangeModel
        oldModel.removeChangeListener(this)
        newModel.addChangeListener(this)
      }
      if ("model" == name || "orientation" == name || "componentOrientation" == name) {
        repaint()
      }
      if ("opaque" == name || "visible" == name) {
        val state = installedState
        if (state != null) {
          state.animationBehavior.onReset()
          state.track.bounds.setBounds(0, 0, 0, 0)
          state.thumb.bounds.setBounds(0, 0, 0, 0)
        }
      }
    }

    fun setValueFrom(event: MouseEvent) {
      val x: Int = event.x
      val y: Int = event.y

      val thumbMin: Int
      val thumbMax: Int
      val thumbPos: Int
      val state = installedState ?: return
      val track = state.track
      val thumb = state.thumb
      val scrollBar = state.scrollBar
      if (Adjustable.VERTICAL == scrollBar.orientation) {
        thumbMin = track.bounds.y
        thumbMax = track.bounds.y + track.bounds.height - thumb.bounds.height
        thumbPos = MathUtil.clamp(y - myOffset, thumbMin, thumbMax)
        if (thumb.bounds.y != thumbPos) {
          val minY: Int = min(thumb.bounds.y, thumbPos)
          val maxY: Int = max(thumb.bounds.y, thumbPos) + thumb.bounds.height
          thumb.bounds.y = thumbPos
          state.animationBehavior.onThumbMove()
          repaint(thumb.bounds.x, minY, thumb.bounds.width, maxY - minY)
        }
      }
      else {
        thumbMin = track.bounds.x
        thumbMax = track.bounds.x + track.bounds.width - thumb.bounds.width
        thumbPos = MathUtil.clamp(x - myOffset, thumbMin, thumbMax)
        if (thumb.bounds.x != thumbPos) {
          val minX: Int = min(thumb.bounds.x, thumbPos)
          val maxX: Int = max(thumb.bounds.x, thumbPos) + thumb.bounds.width
          thumb.bounds.x = thumbPos
          state.animationBehavior.onThumbMove()
          repaint(minX, thumb.bounds.y, maxX - minX, thumb.bounds.height)
        }
      }
      val valueMin = scrollBar.minimum
      val valueMax = scrollBar.maximum - scrollBar.visibleAmount
      // If the thumb has reached the end of the scrollbar, then set the value to its maximum.
      // Otherwise, compute the value as accurately as possible.
      val isDefaultOrientation: Boolean = Adjustable.VERTICAL == scrollBar.orientation || scrollBar.componentOrientation.isLeftToRight
      if (thumbPos == thumbMax) {
        scrollBar.value = if (isDefaultOrientation) valueMax else valueMin
      }
      else {
        val valueRange = valueMax - valueMin
        val thumbRange = thumbMax - thumbMin
        val thumbValue = if (isDefaultOrientation) thumbPos - thumbMin else thumbMax - thumbPos
        isValueCached = true
        cachedValue = valueMin + convert(valueRange.toDouble(), thumbValue.toDouble(), thumbRange.toDouble())
        scrollBar.value = cachedValue
      }
      if (!isDragging) {
        updateMouse(x, y)
      }
    }

    fun startScrollTimerIfNecessary() {
      if (!scrollTimer.isRunning) {
        if (if (isReversed) isMouseBeforeThumb() else isMouseAfterThumb()) {
          scrollTimer.start()
        }
      }
    }

    fun isMouseBeforeThumb(): Boolean {
      return when {
        Adjustable.VERTICAL == installedState!!.scrollBar.orientation -> isMouseOnTop()
        installedState!!.scrollBar.componentOrientation.isLeftToRight -> isMouseOnLeft()
        else -> isMouseOnRight()
      }
    }

    fun isMouseAfterThumb(): Boolean {
      return when {
        Adjustable.VERTICAL == installedState!!.scrollBar.orientation -> isMouseOnBottom()
        installedState!!.scrollBar.componentOrientation.isLeftToRight -> isMouseOnRight()
        else -> isMouseOnLeft()
      }
    }

    fun isMouseOnTop(): Boolean = myMouseY < installedState!!.thumb.bounds.y

    fun isMouseOnLeft(): Boolean = myMouseX < installedState!!.thumb.bounds.x

    fun isMouseOnRight(): Boolean {
      val thumb = installedState!!.thumb
      return myMouseX > thumb.bounds.x + thumb.bounds.width
    }

    fun isMouseOnBottom(): Boolean {
      val thumb = installedState!!.thumb
      return myMouseY > thumb.bounds.y + thumb.bounds.height
    }

    fun scroll(reversed: Boolean) {
      val scrollBar = installedState!!.scrollBar
      var delta: Int = scrollBar.getBlockIncrement(if (reversed) -1 else 1)
      if (reversed) {
        delta = -delta
      }

      val oldValue = scrollBar.value
      var newValue = oldValue + delta

      if (delta > 0 && newValue < oldValue) {
        newValue = scrollBar.maximum
      }
      else if (delta < 0 && newValue > oldValue) {
        newValue = scrollBar.minimum
      }

      if (oldValue != newValue) {
        scrollBar.value = newValue
      }
    }
  }
}

internal class DefaultScrollbarUiInstalledState(ui: DefaultScrollBarUI, @JvmField val scrollBar: JScrollBar) {
  @Suppress("SSBasedInspection")
  @JvmField
  val coroutineScope = CoroutineScope(
    SupervisorJob() +
    Dispatchers.Default +
    (ApplicationManager.getApplication()?.defaultModalityState?.asContextElement() ?: EmptyCoroutineContext) +
    ClientId.coroutineContext()
  )

  @JvmField
  val track: ScrollBarPainter.Track = ScrollBarPainter.Track({ scrollBar }, coroutineScope)

  @JvmField
  val thumb: ScrollBarPainter.Thumb = ui.createThumbPainter(this)

  @JvmField
  val animationBehavior: ScrollBarAnimationBehavior = ui.createWrapAnimationBehaviour(this)
}

private fun addPreferredWidth(preferred: Dimension, component: Component?) {
  if (component != null) {
    val size: Dimension = component.preferredSize
    preferred.width += size.width
    if (preferred.height < size.height) preferred.height = size.height
  }
}

private fun addPreferredHeight(preferred: Dimension, component: Component?) {
  if (component != null) {
    val size: Dimension = component.preferredSize
    preferred.height += size.height
    if (preferred.width < size.width) preferred.width = size.width
  }
}

/**
 * Converts a value from old range to new one.
 * It is necessary to use floating point calculation to avoid integer overflow.
 */
private fun convert(newRange: Double, oldValue: Double, oldRange: Double): Int = (.5 + newRange * oldValue / oldRange).toInt()

private fun adjust(value: Int, min: Int, max: Int): Int = max(min, min(value, max))
