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
import kotlin.collections.plusAssign
import kotlin.math.max

/**
 * This strategy dynamically adjusts component sizes, ranging from their preferred size to their minimum size.
 * Should the parent component lack sufficient space, a compress operation is triggered on its child components.
 * Preferentially, the largest components are compressed first to optimize the use of available space.
 */
@Internal
object CompressingLayoutStrategy : ToolbarLayoutStrategy {
  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    val toolbarComponent = toolbar.component
    val componentCount = toolbarComponent.componentCount

    val nonCompressibleWidth = getNonCompressibleWidth(toolbar.component)
    val availableWidth = toolbar.component.width - nonCompressibleWidth
    val resizableComponents = collectResizableComponents(toolbar)
    val resizableComponentWidths = calculateResizableComponentWidths(availableWidth, resizableComponents)
    val componentWidths = calculateComponentWidths(toolbar, resizableComponentWidths)

    val bounds = List(componentCount) { Rectangle() }
    calculateComponentSizes(toolbarComponent.components, componentWidths, bounds)
    layoutComponents(toolbarComponent, bounds)
    rightAlignComponents(toolbarComponent, bounds)

    return bounds
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    val res = Dimension()
    res.height = experimentalToolbarMinimumButtonSize().height
    for (component in toolbar.component.components) {
      if (!component.isVisible) continue
      val size = component.preferredSize
      res.width += size.width
      res.height = max(res.height, size.height)
    }
    JBInsets.addTo(res, toolbar.component.insets)
    return res
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension {
    return JBUI.emptySize()
  }

  /**
   * Distributes the available size between the given toolbars.
   *
   * Intended to be used by parents that are not toolbars themselves.
   * If such a parent has several toolbar children, then it can't rely on their minimum and preferred sizes alone,
   * because the size of every toolbar will only take into account what's inside it, but not what's inside other toolbars.
   *
   * To get the best result, the parent must distribute the size between the toolbars taking into account their contents.
   * But because the logic of size distribution is encapsulated into this strategy, it has to be exposed as a public method.
   */
  fun distributeSize(availableSize: Dimension, toolbars: List<ActionToolbar>): Map<ActionToolbar, Dimension> {
    if (toolbars.isEmpty()) return emptyMap()
    val resizableComponents = toolbars.associateWith { toolbar -> collectResizableComponents(toolbar) }
    val nonCompressibleWidths = toolbars.associateWith { toolbar -> getNonCompressibleWidth(toolbar.component) }
    val availableWidth = availableSize.width - nonCompressibleWidths.values.sum()
    val resizableComponentWidths = calculateResizableComponentWidths(availableWidth, resizableComponents.values.flatten())
    val height = toolbars.maxOf { toolbar -> toolbar.component.preferredSize.height }
      .coerceAtLeast(experimentalToolbarMinimumButtonSize().height)
    return toolbars.associateWith { toolbar ->
      val width = if (toolbar.component.isVisible) {
        calculateComponentWidths(toolbar, resizableComponentWidths).getValue(toolbar.component)
      }
      else {
        0
      }
      Dimension(width, height)
    }
  }
}

private fun getNonCompressibleWidth(component: Component): Int {
  if (!component.isVisible) return 0
  return when (component.kind) {
    Kind.RESIZABLE_TOOLBAR -> {
      component as JComponent
      var result = component.insets.left + component.insets.right
      for (component in component.components) {
        result += getNonCompressibleWidth(component)
      }
      result
    }
    Kind.NON_RESIZABLE -> component.preferredSize.width
    Kind.RESIZABLE_COMPONENT -> 0
  }
}

private fun collectResizableComponents(toolbar: ActionToolbar): List<Component> {
  if (!toolbar.component.isVisible || toolbar.component.kind != Kind.RESIZABLE_TOOLBAR) return emptyList()
  val result = mutableListOf<Component>()
  for (component in toolbar.component.components) {
    if (!component.isVisible) continue
    if (component is ActionToolbar) {
      result += collectResizableComponents(component)
    }
    else if (component.kind == Kind.RESIZABLE_COMPONENT) {
      result += component
    }
  }
  return result
}

private enum class Kind {
  RESIZABLE_TOOLBAR,
  RESIZABLE_COMPONENT,
  NON_RESIZABLE,
}

private val Component.kind: Kind get() =
  if (!isVisible) {
    Kind.NON_RESIZABLE
  } else if (this is ActionToolbar) {
    if (layoutStrategy is CompressingLayoutStrategy) {
      Kind.RESIZABLE_TOOLBAR
    }
    else {
      Kind.NON_RESIZABLE
    }
  }
  else {
    if (minimumSize.width < preferredSize.width) {
      Kind.RESIZABLE_COMPONENT
    }
    else {
      Kind.NON_RESIZABLE
    }
  }

private fun calculateResizableComponentWidths(availableWidth: Int, components: List<Component>): Map<Component, Int> {
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
    val oldWidth = calculatedWidths.getValue(minWidthComponent)
    val preferredWidth = preferredWidths.getValue(minWidthComponent)
    val maxAllowedWidth = oldWidth + (availableWidth - currentWidthSum)
    val nextCompressibleComponent: Component? = compressibleComponents.peek()
    val newWidth = if (nextCompressibleComponent == null) {
      // This is the last component. Extend it to its preferred size, but not more than actually available.
      preferredWidth.coerceAtMost(maxAllowedWidth)
    }
    else {
      // There are other components.
      // Extend this one so it won't be the first in the queue anymore,
      // but take care to not exceed its preferredWidth or the available width.
      val nextWidth = calculatedWidths.getValue(nextCompressibleComponent)
      (nextWidth + 1).coerceAtMost(maxAllowedWidth).coerceAtMost(preferredWidth)
    }
    calculatedWidths[minWidthComponent] = newWidth
    currentWidthSum += newWidth - oldWidth
    if (newWidth < preferredWidth) {
      compressibleComponents.add(minWidthComponent)
    }
  }
  return calculatedWidths
}

private fun calculateComponentWidths(toolbar: ActionToolbar, resizableComponentWidths: Map<Component, Int>): Map<Component, Int> {
  val result = hashMapOf<Component, Int>()
  calculateComponentWidths(result, toolbar, resizableComponentWidths)
  return result
}

private fun calculateComponentWidths(result: MutableMap<Component, Int>, toolbar: ActionToolbar, resizableComponentWidths: Map<Component, Int>) {
  if (!toolbar.component.isVisible) return
  var toolbarWidth = toolbar.component.insets.left + toolbar.component.insets.right
  for (component in toolbar.component.components) {
    if (!component.isVisible) continue
    when (component.kind) {
      Kind.RESIZABLE_TOOLBAR -> {
        calculateComponentWidths(result, component as ActionToolbar, resizableComponentWidths)
      }
      Kind.RESIZABLE_COMPONENT -> result[component] = resizableComponentWidths.getValue(component)
      Kind.NON_RESIZABLE -> result[component] = component.preferredSize.width
    }
    toolbarWidth += result.getValue(component)
  }
  result[toolbar.component] = toolbarWidth
}

private fun calculateComponentSizes(components: Array<Component>, componentWidths: Map<Component, Int>, bounds: List<Rectangle>) {
  for ((i, component) in components.withIndex()) {
    if (component.isVisible) {
      bounds[i].width = componentWidths.getValue(component)
      bounds[i].height = component.preferredSize.height
    }
    else {
      bounds[i].width = 0
      bounds[i].height = 0
    }
  }
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