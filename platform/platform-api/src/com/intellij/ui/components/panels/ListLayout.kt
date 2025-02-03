// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.panels

import com.intellij.ui.components.panels.ListLayout.Companion.getDeltaFactor
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * This manager will lay out added components along the [majorAxis] ([Axis.X] for horizontal layout, [Axis.Y] for vertical layout)
 * with a specified [gap] between them.
 * To create a horizontal version use [ListLayout.horizontal], vertical - [ListLayout.vertical].
 *
 * Components will have preferred size along the [majorAxis],
 * stretched across the [minorAxis] if not disabled via [defaultGrowPolicy] or constraints and
 * aligned along the [minorAxis] with [minorAxisAlignment].
 *
 * If it is not possible to lay out components along the [majorAxis] at a preferred size, they will be shrunk based on
 * a difference (delta) between their preferred and minimum size ([getDeltaFactor]).
 *
 * Very similar to [javax.swing.BoxLayout], [FlowLayout]
 * and [com.intellij.ui.components.panels.VerticalLayout]/[com.intellij.ui.components.panels.HorizontalLayout].
 *
 * Difference from Box is that it allows setting a consistent gap, has on option of forcing the component to grow along the minor axis
 * and not using [Component.getAlignmentX]/[Component.getAlignmentY].
 *
 * Difference from Vert/Hor is that it properly handles min/pref/max sizes.
 *
 * TODO: handle visual padding
 *
 * @param majorAxis axis along which the components are laid out
 * @param minorAxisAlignment alignment along the minor axis
 * @param defaultGrowPolicy describes the default sizing along the [minorAxis]
 * @param gap space between components along the [majorAxis] (scaled automatically)
 *
 * @see [ListLayout.vertical]
 * @see [ListLayout.horizontal]
 */
@ApiStatus.Experimental
class ListLayout private constructor(
  private val majorAxis: Axis,
  private val minorAxisAlignment: Alignment,
  private val defaultGrowPolicy: GrowPolicy,
  private val gap: Int
) : LayoutManager2 {

  enum class Axis {
    X, Y
  }

  enum class Alignment {
    /**
     * Place component at the start of the minor axis
     */
    START,

    /**
     * Center component
     */
    CENTER,

    /**
     * Place component at the end of the minor axis
     */
    END
  }

  enum class GrowPolicy {
    /**
     * Stretch component along the minor axis
     */
    GROW,

    /**
     * Do NOT stretch component along the minor axis
     */
    NO_GROW
  }

  private val nonDefaultGrowComponents = mutableSetOf<Component>()
  private val nonDefaultAlignmentComponents = mutableMapOf<Component, Alignment>()

  private val minorAxis = if (majorAxis == Axis.X) Axis.Y else Axis.X
  private val gapValue = JBValue.UIInteger("ListLayout.gap", max(0, gap))

  override fun addLayoutComponent(component: Component, constraints: Any?) {
    if (constraints == null || constraints == defaultGrowPolicy) return
    when (constraints) {
      is GrowPolicy -> nonDefaultGrowComponents.add(component)
      is Alignment -> nonDefaultAlignmentComponents[component] = constraints
      else -> throw IllegalArgumentException("Unsupported constraints: $constraints")
    }
  }

  override fun addLayoutComponent(name: String?, component: Component) {
    addLayoutComponent(component, null)
  }

  override fun removeLayoutComponent(component: Component) {
    nonDefaultGrowComponents.remove(component)
  }

  override fun minimumLayoutSize(container: Container): Dimension {
    val visibleComponents = getVisibleComponents(container)
    return getSize(visibleComponents, Component::getMinimumSize).also {
      if (visibleComponents.isNotEmpty()) {
        JBInsets.addTo(it, container.insets)
      }
    }
  }

  override fun preferredLayoutSize(container: Container): Dimension {
    val visibleComponents = getVisibleComponents(container)
    return getSize(visibleComponents, Component::getPreferredSize).also {
      if (visibleComponents.isNotEmpty()) {
        JBInsets.addTo(it, container.insets)
      }
    }
  }

  override fun maximumLayoutSize(target: Container): Dimension {
    val visibleComponents = getVisibleComponents(target)
    return getSize(visibleComponents, Component::getPreferredSize).also {
      if (visibleComponents.isNotEmpty()) {
        JBInsets.addTo(it, target.insets)
      }
      if (minorAxis == Axis.X) it.width = Int.MAX_VALUE else it.height = Int.MAX_VALUE
    }
  }

  private fun getSize(components: Iterable<Component>, dimensionGetter: (Component) -> Dimension): Dimension {
    var majorAxisSpan: Long = 0
    var minorAxisSpan = 0

    var visibleCount = 0
    for (component in components) {
      if (!component.isVisible) continue

      val size = dimensionGetter(component)
      majorAxisSpan += size.on(majorAxis)

      val minorAxisSize = size.on(minorAxis)
      if (minorAxisSize > minorAxisSpan) {
        minorAxisSpan = minorAxisSize
      }
      visibleCount++
    }
    majorAxisSpan += gapValue.get() * (visibleCount - 1).coerceAtLeast(0)

    return getDimension(majorAxisSpan.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), minorAxisSpan)
  }

  override fun layoutContainer(container: Container) {
    val visibleComponents = getVisibleComponents(container)
    if (visibleComponents.isEmpty()) return

    val bounds = Rectangle(Point(0, 0), container.size)
    JBInsets.removeFrom(bounds, container.insets)

    val minorAxisAllocation = bounds.on(minorAxis)

    var majorAxisStart = bounds.startOn(majorAxis)
    val majorAxisAllocation = bounds.on(majorAxis)
    val majorAxisMin = getSize(visibleComponents, Component::getMinimumSize).on(majorAxis)
    val majorAxisPref = getSize(visibleComponents, Component::getPreferredSize).on(majorAxis)
    val majorAxisDeltaFactor = getDeltaFactor(majorAxisMin, majorAxisPref, majorAxisAllocation)

    var majorAxisRoundingErrorSum = 0f
    var lastShrunkComponent: Component? = null

    val gap = gapValue.get()

    for (component in visibleComponents) {
      val minSize = component.minimumSize
      val prefSize = component.preferredSize
      val maxSize = component.maximumSize

      val majorSpanDelta = prefSize.on(majorAxis) - minSize.on(majorAxis)

      var majorAxisSpan = prefSize.on(majorAxis)
      if (majorAxisDeltaFactor < 1 && majorSpanDelta > 0) {
        val deltaFraction = majorSpanDelta * majorAxisDeltaFactor

        // shrinking may produce fractional size, so we accumulate the rounding error
        // this is not precise, but should be good enough
        val roundedDeltaFraction = floor(deltaFraction).toInt()
        majorAxisRoundingErrorSum += deltaFraction - roundedDeltaFraction
        lastShrunkComponent = component
        majorAxisSpan = minSize.on(majorAxis) + roundedDeltaFraction
      }


      val shouldGrow = defaultGrowPolicy == GrowPolicy.GROW || nonDefaultGrowComponents.contains(component)
      val minorAxisSpan = getMinorAxisSpan(
        minSize.on(minorAxis), prefSize.on(minorAxis), maxSize.on(minorAxis), minorAxisAllocation, shouldGrow
      )
      val minorAxisStart = bounds.startOn(minorAxis) + getMinorAxisStartOffset(minorAxisSpan, minorAxisAllocation, nonDefaultAlignmentComponents[component] ?: minorAxisAlignment)


      component.location = getLocation(majorAxisStart, minorAxisStart)
      component.size = getDimension(majorAxisSpan, minorAxisSpan)
      majorAxisStart += majorAxisSpan + gap
    }

    // append integer part of the rounding error to the last component that was shrunk
    if (majorAxisRoundingErrorSum >= 1 && lastShrunkComponent != null) {
      val sum = floor(majorAxisRoundingErrorSum).toInt()

      val currentSize = lastShrunkComponent.size
      lastShrunkComponent.size = getDimension(currentSize.on(majorAxis) + sum, currentSize.on(minorAxis))
    }
  }

  private fun getMinorAxisSpan(min: Int, pref: Int, max: Int, allocation: Int, grow: Boolean): Int {
    if (allocation <= min) return min

    // if max size is less than min we use min as max
    val minMax = max(min, max)
    val possibleMax = min(minMax, allocation)
    if (pref >= possibleMax || grow) {
      return possibleMax
    }

    return pref
  }

  private fun getMinorAxisStartOffset(minorAxisSpan: Int, minorAxisAllocation: Int, alignment: Alignment) =
    when (alignment) {
      Alignment.CENTER -> ((minorAxisAllocation - minorAxisSpan) / 2).coerceAtLeast(0)
      Alignment.END -> max(0, minorAxisAllocation - minorAxisSpan)
      Alignment.START -> 0
    }

  private fun getLocation(majorAxisCoordinate: Int, minorAxisCoordinate: Int): Point =
    when (majorAxis) {
      Axis.X -> Point(majorAxisCoordinate, minorAxisCoordinate)
      Axis.Y -> Point(minorAxisCoordinate, majorAxisCoordinate)
    }

  private fun getDimension(majorAxisSpan: Int, minorAxisSpan: Int): Dimension =
    when (majorAxis) {
      Axis.X -> Dimension(majorAxisSpan, minorAxisSpan)
      Axis.Y -> Dimension(minorAxisSpan, majorAxisSpan)
    }

  override fun getLayoutAlignmentX(target: Container): Float = .5f
  override fun getLayoutAlignmentY(target: Container): Float = .5f

  override fun invalidateLayout(target: Container) {}

  override fun toString(): String =
    when (majorAxis) {
      Axis.X -> "HorizontalListLayout(minorAxisAlignment=$minorAxisAlignment, defaultGrowPolicy=$defaultGrowPolicy, gap=$gap)"
      Axis.Y -> "VerticalListLayout(minorAxisAlignment=$minorAxisAlignment, defaultGrowPolicy=$defaultGrowPolicy, gap=$gap)"
    }

  companion object {
    /**
     * Create a simple horizontal variant - major axis [Axis.X]
     *
     * @param horGap horizontal gap between components (scaled internally)
     * @param vertAlignment vertical alignment ([Alignment.START] - top, [Alignment.CENTER] - center, [Alignment.END] - bottom)
     * @param vertGrow should the component be stretched vertically
     */
    @JvmStatic
    fun horizontal(horGap: Int = 0, vertAlignment: Alignment = Alignment.CENTER, vertGrow: GrowPolicy = GrowPolicy.NO_GROW): ListLayout {
      return ListLayout(Axis.X, vertAlignment, vertGrow, horGap)
    }

    /**
     * Create a simple vertical variant - major axis [Axis.Y]
     *
     * @param vertGap vertical gap between components (scaled internally)
     * @param horAlignment vertical alignment ([Alignment.START] - left, [Alignment.CENTER] - center, [Alignment.END] - right)
     * @param horGrow should the component be stretched horizontally
     */
    @JvmStatic
    fun vertical(vertGap: Int = 0, horAlignment: Alignment = Alignment.START, horGrow: GrowPolicy = GrowPolicy.GROW): ListLayout {
      return ListLayout(Axis.Y, horAlignment, horGrow, vertGap)
    }

    /**
     * How much the difference between min and pref sizes is shrunk
     */
    private fun getDeltaFactor(min: Int, pref: Int, current: Int): Float {
      if (pref <= min) return 0f

      return ((current - min) / (pref - min).toFloat())
        .coerceAtLeast(0f)
        .coerceAtMost(1f)
    }

    private fun Rectangle.startOn(axis: Axis): Int = if (axis == Axis.X) x else y
    private fun Rectangle.on(axis: Axis): Int = if (axis == Axis.X) width else height
    private fun Dimension.on(axis: Axis): Int = if (axis == Axis.X) width else height

    private fun getVisibleComponents(container: Container): List<Component> = container.components.filter { it.isVisible }
  }
}