// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionToolbar.experimentalToolbarMinimumButtonSize
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.util.*
import javax.swing.JComponent
import kotlin.math.max

/**
 * This strategy dynamically adjusts component sizes, ranging from their preferred size to their minimum size.
 * Should the parent component lack sufficient space, a compress operation is triggered on its child components.
 * Preferentially, the largest components are compressed first to optimize the use of available space.
 * Note: for correct work, it's necessary to have a parent component for row with toolbar.
 */
@Internal
open class CompressingLayoutStrategy : ToolbarLayoutStrategy {
  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    val toolbarComponent = toolbar.component
    val componentsCount = toolbarComponent.componentCount
    val bounds = List(componentsCount) { Rectangle() }
    val preferredAndRealSize = getPreferredAndRealWidth(toolbarComponent.parent)

    calculateComponentSizes(preferredAndRealSize, toolbar, bounds)
    layoutComponents(toolbarComponent, bounds)
    rightAlignComponents(toolbarComponent, bounds)

    return bounds
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    val res = Dimension()
    val toolbarComponent = toolbar.component
    val preferredAndRealSize = getPreferredAndRealWidth(toolbarComponent.parent)
    var toolbarWidthRatio = preferredAndRealSize.second / preferredAndRealSize.first
    val componentSizes = calculateComponentSizes(toolbar, preferredAndRealSize)
    toolbarWidthRatio = if (toolbarWidthRatio > 1) 1.0 else toolbarWidthRatio

    val minButtonSize = ActionToolbar.experimentalToolbarMinimumButtonSize()
    toolbar.component.components.forEach {
      if (!it.isVisible || it !is JComponent) return@forEach
      val size = componentSizes[it] ?: it.preferredSize.apply { width = (it.preferredSize.width * toolbarWidthRatio).toInt() }
      size.height = max(size.height, minButtonSize.height)

      res.width += size.width
      res.height = max(res.height, size.height)
    }

    JBInsets.addTo(res, toolbar.component.insets)

    return res
  }

  private fun getPreferredAndRealWidth(mainToolbar: Container): Pair<Double, Double> {
    var totalWidth = 0
    for (i in 0 until mainToolbar.componentCount) {
      val component = mainToolbar.getComponent(i)
      if (component !is JComponent) continue
      val toolbar = component as? ActionToolbar
      if (toolbar != null && toolbar.layoutStrategy is CompressingLayoutStrategy) {
        for (element in toolbar.component.components) {
          if (!element.isVisible || element !is JComponent) continue
          totalWidth += element.preferredSize.width
        }
      }
      else {
        totalWidth += component.preferredSize.width
      }
    }
    val width = mainToolbar.width
    return Pair((totalWidth).toDouble(), (width - getNonCompressibleWidth(mainToolbar)).toDouble())
  }

  protected open fun getNonCompressibleWidth(mainToolbar: Container): Int {
    return mainToolbar.components.filterNot { it is ActionToolbar && it.layoutStrategy is CompressingLayoutStrategy }.sumOf { it.preferredSize.width}
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension {
    return JBUI.emptySize()
  }
}

private fun calculateComponentSizes(toolbar: ActionToolbar, preferredAndRealSize: Pair<Double, Double>): Map<Component, Dimension> {
  val mainToolbar = toolbar.component.parent
  val toolbarWidthDiff = preferredAndRealSize.first - preferredAndRealSize.second
  return if (toolbarWidthDiff > 0) {
    val components = mainToolbar.components.filter { it is ActionToolbar && it.layoutStrategy is CompressingLayoutStrategy }.flatMap {
      (it as? JComponent)?.components?.toList() ?: listOf(it)
    }
    calculateComponentWidths(preferredAndRealSize.second.toInt(), components).map { entry -> Pair(entry.key, Dimension(entry.value, entry.key.preferredSize.height)) }.toMap()
  }
  else {
    mainToolbar.components.flatMap { (it as? Container)?.components?.toList() ?: listOf(it) }.associateWith { it.preferredSize }
  }
}

private fun calculateComponentSizes(
  preferredAndRealSize: Pair<Double, Double>,
  toolbar: ActionToolbar,
  bounds: List<Rectangle>,
) {
  val toolbarComponent = toolbar.component
  var toolbarWidthRatio = preferredAndRealSize.second / preferredAndRealSize.first
  toolbarWidthRatio = if (toolbarWidthRatio > 1) 1.0 else toolbarWidthRatio
  val componentSizes = calculateComponentSizes(toolbar, preferredAndRealSize)
  for (i in bounds.indices) {
    val component: Component = toolbarComponent.getComponent(i)
    val d: Dimension = componentSizes[component]
                       ?: getChildPreferredSize(toolbarComponent, i).apply { width = (width * toolbarWidthRatio).toInt() }
    bounds[i].size = d
  }
}

private fun calculateComponentWidths(availableWidth: Int, components: List<Component>): Map<Component, Int> {
  val preferredWidths = components.associateWith { it.preferredSize.width }
  if (availableWidth >= preferredWidths.values.sum()) {
    return preferredWidths
  }

  val calculatedWidths = components.associateWith { it.minimumSize.width }.toMutableMap()
  var currentWidthSum = calculatedWidths.values.sum()
  // Create a priority queue that polls the smallest width component,
  // prioritizing among such components the one that has the maximum preferred width (most space to grow).
  val compressibleComponents: PriorityQueue<Component> = PriorityQueue(
    compareBy({ calculatedWidths.getValue(it) }, { -preferredWidths.getValue(it) })
  )
  compressibleComponents.addAll(components.filter { calculatedWidths.getValue(it) < preferredWidths.getValue(it) })

  while (currentWidthSum < availableWidth && !compressibleComponents.isEmpty()) {
    val minWidthComponent: Component =  compressibleComponents.remove()
    if (calculatedWidths.getValue(minWidthComponent) < preferredWidths.getValue(minWidthComponent)) {
      currentWidthSum++
      val newWidth = calculatedWidths.getValue(minWidthComponent) + 1
      calculatedWidths[minWidthComponent] = newWidth
      if (newWidth < preferredWidths.getValue(minWidthComponent)) {
        compressibleComponents.add(minWidthComponent)
      }
    }
  }
  return calculatedWidths
}

private fun layoutComponents(toolbarComponent: JComponent, bounds: List<Rectangle>) {
  val toolbarHeight = toolbarComponent.height
  val minHeight = experimentalToolbarMinimumButtonSize().height
  var x = toolbarComponent.insets.left
  for (rect in bounds) {
    rect.height = rect.height.coerceAtLeast(minHeight)
    rect.x = x
    rect.y = (toolbarHeight - rect.height) / 2
    x += rect.width
  }
}

private fun rightAlignComponents(toolbarComponent: JComponent, bounds: List<Rectangle>) {
  val componentWidth = toolbarComponent.width
  var rightOffset = toolbarComponent.insets.right
  for (i in bounds.indices.reversed()) {
    val child: Component = toolbarComponent.getComponent(i)
    if (child is JComponent && child.getClientProperty(RIGHT_ALIGN_KEY) == true) {
      rightOffset += bounds[i].width
      bounds[i].x = componentWidth - rightOffset
    }
  }
}