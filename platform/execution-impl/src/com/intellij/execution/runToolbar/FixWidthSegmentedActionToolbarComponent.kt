// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ComparatorUtil
import com.intellij.util.ui.JBDimension
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent

open class FixWidthSegmentedActionToolbarComponent(place: String, group: ActionGroup) : SegmentedActionToolbarComponent(place, group) {
  companion object {
    private val LOG = Logger.getInstance(FixWidthSegmentedActionToolbarComponent::class.java)
  }

  private var rightSideSize: JBDimension? = null

  override fun calculateBounds(size2Fit: Dimension, bounds: MutableList<Rectangle>) {

    val controlButtonsPrefWidth = ((0 until componentCount).map {
      getComponent(it)
    }.firstOrNull { it is ActionButton }?.preferredSize?.height ?: 0) * RunToolbarProcess.ACTIVE_STATE_BUTTONS_COUNT
    val executorButtonsPrefWidth = (0 until componentCount).mapNotNull {
      val actionComponent = getComponent(it) as JComponent
      val anAction = actionComponent.getClientProperty(RUN_TOOLBAR_COMPONENT_ACTION)

      if (anAction is ExecutorRunToolbarAction && anAction.process.showInBar) {
        getChildPreferredSize(it)
      }
      else null
    }.sumOf { it.width }

    val isMainState = (executorButtonsPrefWidth > 0)
    val isMain = executorButtonsPrefWidth > controlButtonsPrefWidth

    val max = ComparatorUtil.max(executorButtonsPrefWidth, controlButtonsPrefWidth)
    val isNew = (rightSideSize?.width ?: 0) < max

    if (isNew) {
      rightSideSize = JBDimension(max, controlButtonsPrefWidth, true)
    }

    if (isMainState) {
      if (executorButtonsPrefWidth > controlButtonsPrefWidth) {
        if (isNew) {
          if (isMain) {
            super.calculateBounds(size2Fit, bounds)
            return
          }
        }
      }
    }
    rightSideSize?.let {
      calculateBoundsToFit(it, bounds)
      return
    }
    super.calculateBounds(size2Fit, bounds)
  }

  private fun calculateBoundsToFit(size2Fit: Dimension, bounds: MutableList<Rectangle>) {
    val executors = mutableListOf<Component>()
    var flexible = mutableListOf<Component>()
    var stable = mutableListOf<Component>()

    (0 until componentCount).forEach {
      val actionComponent = getComponent(it) as JComponent

      when (val action = actionComponent.getClientProperty(RUN_TOOLBAR_COMPONENT_ACTION)) {
        is ExecutorRunToolbarAction -> {
          executors.add(actionComponent)
        }
        is RunToolbarAction -> {
          when (action.getFlexibleType()) {
            RunToolbarAction.FlexibleType.Flexible -> {
              flexible.add(actionComponent)
            }
            RunToolbarAction.FlexibleType.Stable -> {
              stable.add(actionComponent)
            }
            else -> {
            }
          }
        }
      }
    }

    flexible.ifEmpty {
      if (stable.isNotEmpty()) {
        flexible = stable
        stable = mutableListOf()
      }
    }

    if (executors.isEmpty() && flexible.isEmpty()) {
      LOG.error("right side empty")
      super.calculateBounds(size2Fit, bounds)
      return
    }

    if (executors.isNotEmpty() && flexible.isNotEmpty()) {
      LOG.error("Unexpected run toolbar widget state")
      super.calculateBounds(size2Fit, bounds)
      return
    }

    val flexibleComponents = executors.ifEmpty { flexible }

    val flexiblePrefWidth = (0 until componentCount).filter {
      flexibleComponents.contains(getComponent(it))
    }.sumOf { getChildPreferredSize(it).width }

    val stableWidth = (0 until componentCount).filter {
      stable.contains(getComponent(it))
    }.sumOf { getChildPreferredSize(it).width }

    val delta = size2Fit.width - flexiblePrefWidth - stableWidth
    val gap = if (delta > 0) delta / flexibleComponents.size else 0

    val lastAddGap = if (gap > 0) size2Fit.width - (flexibleComponents.size * gap) - flexiblePrefWidth - stableWidth else 0
    LOG.debug("gap $gap")

    bounds.clear()
    for (i in 0 until componentCount) {
      bounds.add(Rectangle())
    }

    var offset = 0
    var calculatedW = 0
    val last = flexibleComponents[flexibleComponents.lastIndex]
    for (i in 0 until componentCount) {
      val d = getChildPreferredSize(i)
      var w = d.width
      val component = getComponent(i)

      if (flexibleComponents.contains(component)) {
        w += gap
        if(component == last) w+= lastAddGap
        calculatedW += w
      }

      val r = bounds[i]
      r.setBounds(insets.left + offset, insets.top, w, DEFAULT_MINIMUM_BUTTON_SIZE.height)
      offset += w
    }
  }
}
