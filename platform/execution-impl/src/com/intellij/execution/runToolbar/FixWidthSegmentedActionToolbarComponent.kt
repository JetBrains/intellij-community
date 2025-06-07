// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ComparatorUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.TimerUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent

@ApiStatus.Internal
open class FixWidthSegmentedActionToolbarComponent(place: String, group: ActionGroup) : SegmentedActionToolbarComponent(place, group) {

  protected var project: Project? = null
  private var runWidgetWidthHelper: RunWidgetWidthHelper? = null

  init {
    layoutStrategy = MyLayoutStrategy()
  }

  private val listener = object : UpdateWidth {
    override fun updated() {
      updateWidthHandler()
    }
  }

  private var timer = TimerUtil.createNamedTimer("project checker", 50).apply {
    isRepeats = false
    addActionListener {
      checkProject()
    }
  }

  override fun addNotify() {
    super.addNotify()
    checkProject()
  }

  private fun checkProject() {
      CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))?.let {
        updateProject(it)
        runWidgetWidthHelper = RunWidgetWidthHelper.getInstance(it).apply {
          addListener(listener)
        }
      } ?: kotlin.run {
        timer.start()
      }
  }


  override fun removeNotify() {
    runWidgetWidthHelper?.removeListener(listener)
    removeProject()
    timer.stop()
    super.removeNotify()
  }


  protected open fun updateWidthHandler() {
    preferredSize
    revalidate()
    repaint()
  }

  protected open fun updateProject(value: Project) {
    if(project == value) return

    project = value
    Disposer.register(value) {
      removeProject()
    }
  }

  protected open fun removeProject() {
    project = null
  }

  private inner class MyLayoutStrategy: ToolbarLayoutStrategy {
    private val fallbackLayout = ToolbarLayoutStrategy.NOWRAP_STRATEGY

    override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
      val container = toolbar.component
      return doCalculateBounds(container)
    }

    override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
      val container = toolbar.component
      val bounds = doCalculateBounds(container).reduce { acc, rect -> acc.union(rect) }
      JBInsets.addTo(bounds, container.insets)
      return bounds.size
    }

    override fun calcMinimumSize(toolbar: ActionToolbar): Dimension = JBUI.emptySize()

    fun doCalculateBounds(container: Container): List<Rectangle> {

      val controlButtonsPrefWidth = (container.components.firstOrNull { it is ActionButton }?.preferredSize?.height ?: 0) * RunToolbarProcess.ACTIVE_STATE_BUTTONS_COUNT
      val executorButtonsPrefWidth = container.components.mapNotNull {
        val anAction = (it as JComponent).getClientProperty(RUN_TOOLBAR_COMPONENT_ACTION)
        if (anAction is RTBarAction) {
          when (anAction) {
            is ExecutorRunToolbarAction -> getPrefSize(it).width
            is RTRunConfiguration -> {
              if (anAction.isStable()) {
                val configWidth = getPrefSize(it).width.toFloat()
                runWidgetWidthHelper?.runConfigWidth?.let { float ->
                  if (configWidth > float.float) {
                    runWidgetWidthHelper?.runConfigWidth = JBValue.Float(configWidth, true)
                  }
                } ?: kotlin.run {
                  runWidgetWidthHelper?.runConfigWidth = JBValue.Float(configWidth, true)
                }
              }
              null
            }
            else -> null
          }
        }
        else null
      }.sumOf { it }

      val max = ComparatorUtil.max(executorButtonsPrefWidth, controlButtonsPrefWidth)

      if ((runWidgetWidthHelper?.rightSideWidth?.get() ?: 0) < max) {
        runWidgetWidthHelper?.rightSideWidth = JBValue.Float(max.toFloat(), true)
      }

      return runWidgetWidthHelper?.rightSideWidth?.let { calculateBoundsToFit() }
             ?: fallbackLayout.calculateBounds(this@FixWidthSegmentedActionToolbarComponent)
    }

    private fun getPrefSize(cmp: Component) = if (cmp.isVisible) cmp.preferredSize else Dimension()

    private fun calculateBoundsToFit(): List<Rectangle> {
      val right_flexible = mutableListOf<Component>()
      val right_stable = mutableListOf<Component>()
      val flexible = mutableListOf<Component>()

      (0 until componentCount).forEach {
        val actionComponent = getComponent(it) as JComponent
        actionComponent.getClientProperty(RUN_TOOLBAR_COMPONENT_ACTION)?.let { action ->
          if (action is RTBarAction) {
            if (action is RTRunConfiguration) {
              (if (action.isStable()) null else flexible)?.add(actionComponent)
            }
            else {
              when (action.getRightSideType()) {
                RTBarAction.Type.RIGHT_FLEXIBLE -> right_flexible
                RTBarAction.Type.RIGHT_STABLE -> right_stable
                RTBarAction.Type.FLEXIBLE -> flexible
                else -> null
              }?.add(actionComponent)
            }

          }
        }
      }

      runWidgetWidthHelper?.rightSideWidth?.get()?.let { rightWidth ->
        val bounds = mutableListOf<Rectangle>()
        for (i in 0 until componentCount) {
          bounds.add(Rectangle())
        }

        if (right_flexible.isNotEmpty()) {
          val flexiblePrefWidth = (0 until componentCount).filter {
            right_flexible.contains(getComponent(it))
          }.sumOf { getChildPreferredSize(it).width }

          val stablePrefWidth = (0 until componentCount).filter {
            right_stable.contains(getComponent(it))
          }.sumOf { getChildPreferredSize(it).width }

          val delta = rightWidth - stablePrefWidth - flexiblePrefWidth
          val rightAdditionWidth = if (delta > 0) delta / right_flexible.size else 0

          val lastAddition = if(delta <= 0) 0 else rightWidth - stablePrefWidth - flexiblePrefWidth - (rightAdditionWidth * right_flexible.size).let { gap ->
            if (gap < 0) 0 else gap
          }

          setComponentsBounds(right_flexible, rightAdditionWidth, lastAddition, bounds)

          return bounds
        }
        else if (flexible.size == 1) {
          val stablePrefWidth = (0 until componentCount).filter {
            right_stable.contains(getComponent(it))
          }.sumOf { getChildPreferredSize(it).width }


          return runWidgetWidthHelper?.configWithArrow?.let {
            (rightWidth + it - stablePrefWidth).let {
              if (it > 0) it else null
            }
          }?.let {
            var offset = 0
            for (i in 0 until componentCount) {
              val d = getChildPreferredSize(i)
              var w = d.width
              val component = getComponent(i)
              if (component == flexible[0]) {
                w = it
              }

              val r = bounds[i]
              r.setBounds(insets.left + offset, insets.top, w, DEFAULT_MINIMUM_BUTTON_SIZE.height)
              offset += w
            }
            bounds
          } ?: fallbackLayout.calculateBounds(this@FixWidthSegmentedActionToolbarComponent)
        } else if(right_stable.isNotEmpty() && flexible.isEmpty()) {
          val stablePrefWidth = (0 until componentCount).filter {
            right_stable.contains(getComponent(it))
          }.sumOf { getChildPreferredSize(it).width }

          val delta = rightWidth - stablePrefWidth
          val rightAdditionWidth = if (delta > 0) delta / right_stable.size else 0

          val lastAddition = if(delta <= 0) 0 else rightWidth - stablePrefWidth - (rightAdditionWidth * right_stable.size).let { gap ->
            if (gap < 0) 0 else gap
          }

          setComponentsBounds(right_stable, rightAdditionWidth, lastAddition, bounds)

          return bounds
        } else {
          return fallbackLayout.calculateBounds(this@FixWidthSegmentedActionToolbarComponent)
        }
      } ?: return fallbackLayout.calculateBounds(this@FixWidthSegmentedActionToolbarComponent)
    }

    private fun setComponentsBounds(flexible: MutableList<Component>,
                                    additionWidth: Int,
                                    lastAddition: Int,
                                    bounds: MutableList<Rectangle>) {
      var offset = 0
      val last = flexible[flexible.lastIndex]
      for (i in 0 until componentCount) {
        val d = getChildPreferredSize(i)
        var w = d.width
        val component = getComponent(i)

        if (flexible.contains(component)) {
          w += additionWidth
          if (component == last) w += lastAddition
        }

        val r = bounds[i]
        r.setBounds(insets.left + offset, insets.top, w, DEFAULT_MINIMUM_BUTTON_SIZE.height)
        offset += w
      }
    }
  }
}
