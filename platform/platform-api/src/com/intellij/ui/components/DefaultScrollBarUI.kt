// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.MathUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.UIUtil.ComponentStyle
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.plaf.ScrollBarUI
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
open class DefaultScrollBarUI @JvmOverloads internal constructor(
  private val myThickness: Int = if (ScrollSettings.isThumbSmallIfOpaque.invoke()) 13 else 10,
  private val myThicknessMax: Int = 14,
  private val myThicknessMin: Int = 10
) : ScrollBarUI() {
  private val myListener: Listener = Listener()
  private val myScrollTimer: Timer = TimerUtil.createNamedTimer("ScrollBarThumbScrollTimer", 60, myListener)

  @JvmField
  var myScrollBar: JScrollBar? = null

  @JvmField
  protected val myTrack: ScrollBarPainter.Track = ScrollBarPainter.Track({ myScrollBar })
  @JvmField
  val myThumb: ScrollBarPainter.Thumb = createThumbPainter()

  private var isValueCached: Boolean = false
  private var myCachedValue: Int = 0
  private var myOldValue: Int = 0

  @JvmField
  protected var myAnimationBehavior: ScrollBarAnimationBehavior? = null

  companion object {
    @ApiStatus.Internal
    @JvmField
    val LEADING: Key<Component> = Key.create("JB_SCROLL_BAR_LEADING_COMPONENT")

    @JvmField
    @ApiStatus.Internal
    val TRAILING: Key<Component> = Key.create("JB_SCROLL_BAR_TRAILING_COMPONENT")

    @JvmStatic
    fun isOpaque(c: Component): Boolean {
      if (c.isOpaque) {
        return true
      }

      val parent = c.parent
      // do not allow non-opaque scroll bars, because default layout does not support them
      return parent is JScrollPane && parent.getLayout() is ScrollPaneLayout.UIResource
    }
  }

  protected open fun createWrapAnimationBehaviour(): ScrollBarAnimationBehavior {
    return ToggleableScrollBarAnimationBehaviorDecorator(
      decoratedBehavior = createBaseAnimationBehavior(),
      trackAnimator = myTrack.animator,
      thumbAnimator = myThumb.animator,
    )
  }

  protected open fun createThumbPainter(): ScrollBarPainter.Thumb {
    return ScrollBarPainter.Thumb({ myScrollBar }, false)
  }

  protected open fun createBaseAnimationBehavior(): ScrollBarAnimationBehavior {
    return DefaultScrollBarAnimationBehavior(trackAnimator = myTrack.animator, thumbAnimator = myThumb.animator)
  }

  val thickness: Int
    get() = scale(if (myScrollBar == null || isOpaque(myScrollBar!!)) myThickness else myThicknessMax)

  val minimalThickness: Int
    get() = scale(if (myScrollBar == null || isOpaque(myScrollBar!!)) myThickness else myThicknessMin)

  fun toggle(isOn: Boolean) {
    if (myAnimationBehavior != null) {
      myAnimationBehavior!!.onToggle(isOn)
    }
  }

  open fun isAbsolutePositioning(event: MouseEvent): Boolean = SwingUtilities.isMiddleMouseButton(event)

  open fun isTrackClickable(): Boolean = isOpaque(myScrollBar!!) || (myAnimationBehavior != null && myAnimationBehavior!!.trackFrame > 0)

  open val isTrackExpandable: Boolean
    get() = false

  fun isTrackContains(x: Int, y: Int): Boolean {
    return myTrack.bounds.contains(x, y)
  }

  fun isThumbContains(x: Int, y: Int): Boolean {
    return myThumb.bounds.contains(x, y)
  }

  protected open fun paintTrack(g: Graphics2D, c: JComponent?) {
    paint(myTrack, g, c, false)
  }

  protected open fun paintThumb(g: Graphics2D, c: JComponent) {
    paint(myThumb, g, c, ScrollSettings.isThumbSmallIfOpaque.invoke() && isOpaque(c))
  }

  fun paint(p: ScrollBarPainter, g: Graphics2D, c: JComponent?, small: Boolean) {
    var x: Int = p.bounds.x
    var y: Int = p.bounds.y
    var width: Int = p.bounds.width
    var height: Int = p.bounds.height

    val alignment: JBScrollPane.Alignment = JBScrollPane.Alignment.get(c)
    if (alignment == JBScrollPane.Alignment.LEFT || alignment == JBScrollPane.Alignment.RIGHT) {
      val offset: Int = getTrackOffset(width - minimalThickness)
      if (offset > 0) {
        width -= offset
        if (alignment == JBScrollPane.Alignment.RIGHT) x += offset
      }
    }
    else {
      val offset: Int = getTrackOffset(height - minimalThickness)
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
    if (!isTrackExpandable) return offset
    val value: Float = if (myAnimationBehavior == null) 0f else myAnimationBehavior!!.trackFrame
    if (value <= 0) return offset
    if (value >= 1) return 0
    return (.5f + offset * (1 - value)).toInt()
  }

  fun repaint() {
    if (myScrollBar != null) myScrollBar!!.repaint()
  }

  fun repaint(x: Int, y: Int, width: Int, height: Int) {
    if (myScrollBar != null) myScrollBar!!.repaint(x, y, width, height)
  }

  private fun scale(value: Int): Int {
    val scaledValue = JBUIScale.scale(value)
    return when (UIUtil.getComponentStyle(myScrollBar)) {
      ComponentStyle.LARGE -> (scaledValue * 1.15).toInt()
      ComponentStyle.SMALL -> (scaledValue * 0.857).toInt()
      ComponentStyle.MINI -> (scaledValue * 0.714).toInt()
      ComponentStyle.REGULAR -> scaledValue
    }
  }

  override fun installUI(c: JComponent) {
    myAnimationBehavior = createWrapAnimationBehaviour()

    myScrollBar = c as JScrollBar
    ScrollBarPainter.setBackground(c)
    myScrollBar!!.isOpaque = false
    myScrollBar!!.isFocusable = false
    myScrollBar!!.addMouseListener(myListener)
    myScrollBar!!.addMouseMotionListener(myListener)
    myScrollBar!!.model.addChangeListener(myListener)
    myScrollBar!!.addPropertyChangeListener(myListener)
    myScrollBar!!.addFocusListener(myListener)
    myScrollTimer.initialDelay = 300
  }

  override fun uninstallUI(c: JComponent) {
    checkNotNull(myAnimationBehavior)
    myAnimationBehavior!!.onUninstall()
    myAnimationBehavior = null

    myScrollTimer.stop()
    myScrollBar!!.removeFocusListener(myListener)
    myScrollBar!!.removePropertyChangeListener(myListener)
    myScrollBar!!.model.removeChangeListener(myListener)
    myScrollBar!!.removeMouseMotionListener(myListener)
    myScrollBar!!.removeMouseListener(myListener)
    myScrollBar!!.foreground = null
    myScrollBar!!.background = null
    myScrollBar = null
  }

  override fun getPreferredSize(c: JComponent): Dimension {
    val thickness: Int = myThickness
    val alignment: JBScrollPane.Alignment = JBScrollPane.Alignment.get(c)
    val preferred = Dimension(thickness, thickness)
    if (alignment == JBScrollPane.Alignment.LEFT || alignment == JBScrollPane.Alignment.RIGHT) {
      preferred.height += preferred.height
      addPreferredHeight(preferred, ClientProperty.get(myScrollBar, LEADING))
      addPreferredHeight(preferred, ClientProperty.get(myScrollBar, TRAILING))
    }
    else {
      preferred.width += preferred.width
      addPreferredWidth(preferred, ClientProperty.get(myScrollBar, LEADING))
      addPreferredWidth(preferred, ClientProperty.get(myScrollBar, TRAILING))
    }
    return preferred
  }

  override fun paint(g: Graphics, c: JComponent) {
    val alignment: JBScrollPane.Alignment? = JBScrollPane.Alignment.get(c)
    if (alignment != null && g is Graphics2D) {
      val background: Color? = if (!isOpaque(c)) null else c.background
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
          val size: Int = leading.preferredSize.height
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
        val offset: Int = bounds.width - myThickness
        if (offset > 0) {
          bounds.width -= offset
          if (alignment == JBScrollPane.Alignment.RIGHT) bounds.x += offset
        }
      }
      else {
        val offset: Int = bounds.height - myThickness
        if (offset > 0) {
          bounds.height -= offset
          if (alignment == JBScrollPane.Alignment.BOTTOM) bounds.y += offset
        }
      }
      val animate: Boolean = myTrack.bounds != bounds // animate thumb on resize
      if (animate) myTrack.bounds.bounds = bounds
      updateThumbBounds(animate)
      paintTrack(g, c)
      // process additional drawing on the track
      val track: RegionPainter<Any>? = ClientProperty.get(c, JBScrollBar.TRACK)
      if (track != null && myTrack.bounds.width > 0 && myTrack.bounds.height > 0) {
        track.paint(g, myTrack.bounds.x, myTrack.bounds.y, myTrack.bounds.width, myTrack.bounds.height, null)
      }
      // process drawing the thumb
      if (myThumb.bounds.width > 0 && myThumb.bounds.height > 0) {
        paintThumb(g, c)
      }
    }
  }

  private fun updateThumbBounds(animate: Boolean) {
    var animate: Boolean = animate
    var value = 0
    val min: Int = myScrollBar!!.minimum
    val max: Int = myScrollBar!!.maximum
    val range: Int = max - min
    if (range <= 0) {
      myThumb.bounds.setBounds(0, 0, 0, 0)
    }
    else if (Adjustable.VERTICAL == myScrollBar!!.orientation) {
      val extent = myScrollBar!!.visibleAmount
      val height = max(
        convert(newRange = myTrack.bounds.height.toDouble(), oldValue = extent.toDouble(), oldRange = range.toDouble()).toDouble(),
        (2 * myThickness).toDouble(),
      ).toInt()
      if (myTrack.bounds.height <= height) {
        myThumb.bounds.setBounds(0, 0, 0, 0)
      }
      else {
        value = this.value
        val maxY = myTrack.bounds.y + myTrack.bounds.height - height
        val y = if ((value < max - extent)) {
          convert(
            newRange = (myTrack.bounds.height - height).toDouble(),
            oldValue = (value - min).toDouble(),
            oldRange = (range - extent).toDouble(),
          )
        }
        else {
          maxY
        }
        myThumb.bounds.setBounds(myTrack.bounds.x, adjust(y, myTrack.bounds.y, maxY), myTrack.bounds.width, height)
        animate = animate or (myOldValue != value) // animate thumb on move
      }
    }
    else {
      val extent: Int = myScrollBar!!.visibleAmount
      val width: Int = max(convert(myTrack.bounds.width.toDouble(), extent.toDouble(), range.toDouble()).toDouble(),
                           (2 * myThickness).toDouble()).toInt()
      if (myTrack.bounds.width <= width) {
        myThumb.bounds.setBounds(0, 0, 0, 0)
      }
      else {
        value = this.value
        val maxX: Int = myTrack.bounds.x + myTrack.bounds.width - width
        var x: Int = if ((value < max - extent)) convert((myTrack.bounds.width - width).toDouble(), (value - min).toDouble(),
                                                         (range - extent).toDouble())
        else maxX
        if (!myScrollBar!!.componentOrientation.isLeftToRight) x = myTrack.bounds.x - x + maxX
        myThumb.bounds.setBounds(adjust(x, myTrack.bounds.x, maxX), myTrack.bounds.y, width, myTrack.bounds.height)
        animate = animate or (myOldValue != value) // animate thumb on move
      }
    }
    myOldValue = value
    if (animate && myAnimationBehavior != null) {
      myAnimationBehavior!!.onThumbMove()
    }
  }

  private val value: Int
    get() = if (isValueCached) myCachedValue else myScrollBar!!.value

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
        if (!isOverTrack && myAnimationBehavior != null) {
          myAnimationBehavior!!.onTrackHover(true.also { isOverTrack = it })
        }
        val hover: Boolean = isThumbContains(x, y)
        if (isOverThumb != hover && myAnimationBehavior != null) {
          myAnimationBehavior!!.onThumbHover(hover.also { isOverThumb = it })
        }
      }
      else {
        updateMouseExit()
      }
    }

    fun updateMouseExit() {
      if (isOverThumb && myAnimationBehavior != null) {
        myAnimationBehavior!!.onThumbHover(false.also { isOverThumb = it })
      }
      if (isOverTrack && myAnimationBehavior != null) {
        myAnimationBehavior!!.onTrackHover(false.also { isOverTrack = it })
      }
    }

    fun redispatchIfTrackNotClickable(event: MouseEvent): Boolean {
      if (isTrackClickable()) {
        return false
      }

      // redispatch current event to the view
      val parent: Container = myScrollBar!!.parent
      if (parent is JScrollPane) {
        val view: Component? = parent.viewport.view
        if (view != null) {
          val point: Point = event.locationOnScreen
          SwingUtilities.convertPointFromScreen(point, view)
          val target: Component = SwingUtilities.getDeepestComponentAt(view, point.x, point.y)
          MouseEventAdapter.redispatch(event, target)
        }
      }
      return true
    }

    override fun mouseClicked(e: MouseEvent) {
      if (myScrollBar != null && myScrollBar!!.isEnabled) redispatchIfTrackNotClickable(e)
    }

    override fun mousePressed(event: MouseEvent) {
      if (myScrollBar == null || !myScrollBar!!.isEnabled) return
      if (redispatchIfTrackNotClickable(event)) return
      if (SwingUtilities.isRightMouseButton(event)) return

      isValueCached = true
      myCachedValue = myScrollBar!!.value
      myScrollBar!!.valueIsAdjusting = true

      myMouseX = event.x
      myMouseY = event.y

      val vertical: Boolean = Adjustable.VERTICAL == myScrollBar!!.orientation
      if (isThumbContains(myMouseX, myMouseY)) {
        // pressed on the thumb
        myOffset = if (vertical) (myMouseY - myThumb.bounds.y) else (myMouseX - myThumb.bounds.x)
        isDragging = true
      }
      else if (isTrackContains(myMouseX, myMouseY)) {
        // pressed on the track
        if (isAbsolutePositioning(event)) {
          myOffset = (if (vertical) myThumb.bounds.height else myThumb.bounds.width) / 2
          isDragging = true
          setValueFrom(event)
        }
        else {
          myScrollTimer.stop()
          isDragging = false
          if (Adjustable.VERTICAL == myScrollBar!!.orientation) {
            val y: Int = if (myThumb.bounds.isEmpty) myScrollBar!!.height / 2 else myThumb.bounds.y
            isReversed = myMouseY < y
          }
          else {
            val x: Int = if (myThumb.bounds.isEmpty) myScrollBar!!.width / 2 else myThumb.bounds.x
            isReversed = myMouseX < x
            if (!myScrollBar!!.componentOrientation.isLeftToRight) {
              isReversed = !isReversed
            }
          }
          scroll(isReversed)
          startScrollTimerIfNecessary()
        }
      }
    }

    override fun mouseReleased(event: MouseEvent) {
      if (isDragging) updateMouse(event.x, event.y)
      if (myScrollBar == null || !myScrollBar!!.isEnabled) return
      myScrollBar!!.valueIsAdjusting = false
      if (redispatchIfTrackNotClickable(event)) return
      if (SwingUtilities.isRightMouseButton(event)) return
      isDragging = false
      myOffset = 0
      myScrollTimer.stop()
      isValueCached = true
      myCachedValue = myScrollBar!!.value
      repaint()
    }

    override fun mouseDragged(event: MouseEvent) {
      if (myScrollBar == null || !myScrollBar!!.isEnabled) return
      if (myThumb.bounds.isEmpty || SwingUtilities.isRightMouseButton(event)) return
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
      if (myScrollBar == null || !myScrollBar!!.isEnabled) return
      if (!isDragging) updateMouse(event.x, event.y)
    }

    override fun mouseExited(event: MouseEvent) {
      if (myScrollBar == null || !myScrollBar!!.isEnabled) return
      if (!isDragging) updateMouseExit()
    }

    override fun actionPerformed(event: ActionEvent) {
      if (myScrollBar == null) {
        myScrollTimer.stop()
      }
      else {
        scroll(isReversed)
        if (!myThumb.bounds.isEmpty) {
          if (if (isReversed) !isMouseBeforeThumb() else !isMouseAfterThumb()) {
            myScrollTimer.stop()
          }
        }
        val value: Int = myScrollBar!!.value
        if (if (isReversed) value <= myScrollBar!!.minimum else value >= myScrollBar!!.maximum - myScrollBar!!.visibleAmount) {
          myScrollTimer.stop()
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
        if (myAnimationBehavior != null) {
          myAnimationBehavior!!.onReset()
        }
        myTrack.bounds.setBounds(0, 0, 0, 0)
        myThumb.bounds.setBounds(0, 0, 0, 0)
      }
    }

    fun setValueFrom(event: MouseEvent) {
      val x: Int = event.x
      val y: Int = event.y

      val thumbMin: Int
      val thumbMax: Int
      val thumbPos: Int
      if (Adjustable.VERTICAL == myScrollBar!!.orientation) {
        thumbMin = myTrack.bounds.y
        thumbMax = myTrack.bounds.y + myTrack.bounds.height - myThumb.bounds.height
        thumbPos = MathUtil.clamp(y - myOffset, thumbMin, thumbMax)
        if (myThumb.bounds.y != thumbPos) {
          val minY: Int = min(myThumb.bounds.y.toDouble(), thumbPos.toDouble()).toInt()
          val maxY: Int = (max(myThumb.bounds.y.toDouble(), thumbPos.toDouble()) + myThumb.bounds.height).toInt()
          myThumb.bounds.y = thumbPos
          if (myAnimationBehavior != null) {
            myAnimationBehavior!!.onThumbMove()
          }
          repaint(myThumb.bounds.x, minY, myThumb.bounds.width, maxY - minY)
        }
      }
      else {
        thumbMin = myTrack.bounds.x
        thumbMax = myTrack.bounds.x + myTrack.bounds.width - myThumb.bounds.width
        thumbPos = MathUtil.clamp(x - myOffset, thumbMin, thumbMax)
        if (myThumb.bounds.x != thumbPos) {
          val minX: Int = min(myThumb.bounds.x.toDouble(), thumbPos.toDouble()).toInt()
          val maxX: Int = (max(myThumb.bounds.x.toDouble(), thumbPos.toDouble()) + myThumb.bounds.width).toInt()
          myThumb.bounds.x = thumbPos
          if (myAnimationBehavior != null) {
            myAnimationBehavior!!.onThumbMove()
          }
          repaint(minX, myThumb.bounds.y, maxX - minX, myThumb.bounds.height)
        }
      }
      val valueMin: Int = myScrollBar!!.minimum
      val valueMax: Int = myScrollBar!!.maximum - myScrollBar!!.visibleAmount
      // If the thumb has reached the end of the scrollbar, then set the value to its maximum.
      // Otherwise, compute the value as accurately as possible.
      val isDefaultOrientation: Boolean = Adjustable.VERTICAL == myScrollBar!!.orientation || myScrollBar!!.componentOrientation.isLeftToRight
      if (thumbPos == thumbMax) {
        myScrollBar!!.value = if (isDefaultOrientation) valueMax else valueMin
      }
      else {
        val valueRange = valueMax - valueMin
        val thumbRange = thumbMax - thumbMin
        val thumbValue = if (isDefaultOrientation) thumbPos - thumbMin else thumbMax - thumbPos
        isValueCached = true
        myCachedValue = valueMin + convert(valueRange.toDouble(), thumbValue.toDouble(), thumbRange.toDouble())
        myScrollBar!!.value = myCachedValue
      }
      if (!isDragging) updateMouse(x, y)
    }

    fun startScrollTimerIfNecessary() {
      if (!myScrollTimer.isRunning) {
        if (if (isReversed) isMouseBeforeThumb() else isMouseAfterThumb()) {
          myScrollTimer.start()
        }
      }
    }

    fun isMouseBeforeThumb(): Boolean {
      return when {
        Adjustable.VERTICAL == myScrollBar!!.orientation -> isMouseOnTop()
        myScrollBar!!.componentOrientation.isLeftToRight -> isMouseOnLeft()
        else -> isMouseOnRight()
      }
    }

    fun isMouseAfterThumb(): Boolean {
      return when {
        Adjustable.VERTICAL == myScrollBar!!.orientation -> isMouseOnBottom()
        myScrollBar!!.componentOrientation.isLeftToRight -> isMouseOnRight()
        else -> isMouseOnLeft()
      }
    }

    fun isMouseOnTop(): Boolean {
      return myMouseY < myThumb.bounds.y
    }

    fun isMouseOnLeft(): Boolean {
      return myMouseX < myThumb.bounds.x
    }

    fun isMouseOnRight(): Boolean {
      return myMouseX > myThumb.bounds.x + myThumb.bounds.width
    }

    fun isMouseOnBottom(): Boolean {
      return myMouseY > myThumb.bounds.y + myThumb.bounds.height
    }

    fun scroll(reversed: Boolean) {
      var delta: Int = myScrollBar!!.getBlockIncrement(if (reversed) -1 else 1)
      if (reversed) delta = -delta

      val oldValue: Int = myScrollBar!!.value
      var newValue: Int = oldValue + delta

      if (delta > 0 && newValue < oldValue) {
        newValue = myScrollBar!!.maximum
      }
      else if (delta < 0 && newValue > oldValue) {
        newValue = myScrollBar!!.minimum
      }
      if (oldValue != newValue) {
        myScrollBar!!.value = newValue
      }
    }
  }
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

private fun adjust(value: Int, min: Int, max: Int): Int = max(min.toDouble(), min(value.toDouble(), max.toDouble())).toInt()
