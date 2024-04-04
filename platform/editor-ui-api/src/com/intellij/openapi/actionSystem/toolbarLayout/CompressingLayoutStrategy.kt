// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent
import kotlin.math.max

/**
 * This strategy dynamically adjusts component sizes, ranging from their preferred size to their minimum size.
 * Should the parent component lack sufficient space, a compress operation is triggered on its child components.
 * Preferentially, the largest components are compressed first to optimize the use of available space.
 * Note: for correct work, it's necessary to have a parent component for row with toolbar.
 */
open class CompressingLayoutStrategy : ToolbarLayoutStrategy {
  override fun calculateBounds(toolbar: ActionToolbar): MutableList<Rectangle> {
    val toolbarComponent = toolbar.component
    val componentsCount = toolbarComponent.componentCount
    val insets: Insets = toolbarComponent.insets
    val bounds = MutableList(componentsCount) { Rectangle() }
    val maxHeight: Int = maxComponentPreferredHeight(toolbarComponent)
    var offset = insets.left
    val preferredAndRealSize = getPreferredAndRealWidth(toolbarComponent.parent)
    var toolbarWidthRatio = preferredAndRealSize.second / preferredAndRealSize.first
    toolbarWidthRatio = if (toolbarWidthRatio > 1) 1.0 else toolbarWidthRatio
    val componentSizes = calculateComponentSizes(toolbar, preferredAndRealSize)
    for (i in 0 until componentsCount) {
      val component = toolbarComponent.getComponent(i) as? JComponent ?: continue
      val d: Dimension = componentSizes[component]
                         ?: getChildPreferredSize(toolbarComponent, i).apply { width = (width * toolbarWidthRatio).toInt() }
      val r: Rectangle = bounds[i]
      r.setBounds(offset, insets.top + (maxHeight - d.height) / 2, d.width, d.height)
      offset += d.width
    }

    for (i in 0 until bounds.size) {
      val prevRect = if (i > 0) bounds[i - 1] else null
      val rect = bounds[i]
      fitRectangle(prevRect, rect, toolbarComponent.height)
    }
    val componentWidth = toolbarComponent.width

    if (componentsCount > 0) {
      var rightOffset = insets.right
      var i = componentsCount - 1
      var j = 1
      while (i > 0) {
        val child: Component = toolbarComponent.getComponent(i)
        if (child is JComponent && child.getClientProperty(RIGHT_ALIGN_KEY) == true) {
          rightOffset += bounds[i].width
          val r: Rectangle = bounds[bounds.size - j]
          r.x = componentWidth - rightOffset
        }
        i--
        j++
      }
    }

    return bounds
  }

  /**
   * Calculate the maximum preferred height of the components in the given parent container.
   *
   * @param parent The parent container containing the components.
   * @return The maximum preferred height of the components. Returns 0 if there are no visible components.
   */
  private fun maxComponentPreferredHeight(parent: Container): Int = parent.components
                                                                      .filter { it.isVisible }
                                                                      .map { it.preferredSize }
                                                                      .maxOfOrNull { it.height } ?: 0


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

  private fun calculateComponentSizes(toolbar: ActionToolbar, preferredAndRealSize: Pair<Double, Double>): Map<Component, Dimension> {
    val mainToolbar = toolbar.component.parent
    val toolbarWidthDiff = preferredAndRealSize.first - preferredAndRealSize.second
    return if (toolbarWidthDiff > 0) {
      val components = mainToolbar.components.filter { it is ActionToolbar && it.layoutStrategy is CompressingLayoutStrategy }.flatMap {
        (it as? JComponent)?.components?.toList() ?: listOf(it)
      }
      calculateComponentWidths(preferredAndRealSize.second, components).map { entry -> Pair(entry.key, Dimension(entry.value, entry.key.preferredSize.height)) }.toMap()
    }
    else {
      mainToolbar.components.flatMap { (it as? Container)?.components?.toList() ?: listOf(it) }.associateWith { it.preferredSize }
    }
  }

  private fun calculateComponentWidths(availableWidth: Double, components: List<Component>): Map<Component, Int> {
    if (availableWidth >= components.sumOf { it.preferredSize.width}) {
      return components.associateWith { it.preferredSize.width }
    }
    val componentWidths = components.associateWith { it.minimumSize.width}.toMutableMap()
    while (availableWidth > componentWidths.values.sum()) {
      val minWidthComponent = componentWidths.filter { it.value < it.key.preferredSize.width }.minByOrNull { it.value }?.key
      if (minWidthComponent != null) {
        componentWidths[minWidthComponent] = componentWidths[minWidthComponent]!! + 1
      }
      else {
        return componentWidths
      }
    }
    return componentWidths
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension {
    return JBUI.emptySize()
  }

  private fun fitRectangle(prevRect: Rectangle?, currRect: Rectangle, toolbarHeight: Int) {
    val minButtonSize = ActionToolbar.experimentalToolbarMinimumButtonSize()
    currRect.height = max(currRect.height, minButtonSize.height)

    if (currRect.x == Int.MAX_VALUE || currRect.y == Int.MAX_VALUE) return

    if (prevRect != null && prevRect.maxX > currRect.minX) {
      currRect.x = prevRect.maxX.toInt()
    }
    currRect.y = (toolbarHeight - currRect.height) / 2
  }
}