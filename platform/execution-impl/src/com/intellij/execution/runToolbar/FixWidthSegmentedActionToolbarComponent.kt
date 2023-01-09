// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ComparatorUtil
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.TimerUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent

open class FixWidthSegmentedActionToolbarComponent(place: String, group: ActionGroup) : SegmentedActionToolbarComponent(place, group) {

  protected var project: Project? = null
  private var runWidgetWidthHelper: RunWidgetWidthHelper? = null

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

  override fun calculateBounds(size2Fit: Dimension, bounds: MutableList<Rectangle>) {

    val controlButtonsPrefWidth = ((0 until componentCount).map {
      getComponent(it)
    }.firstOrNull { it is ActionButton }?.preferredSize?.height ?: 0) * RunToolbarProcess.ACTIVE_STATE_BUTTONS_COUNT
    val executorButtonsPrefWidth = (0 until componentCount).mapNotNull {
      val actionComponent = getComponent(it) as JComponent
      val anAction = actionComponent.getClientProperty(RUN_TOOLBAR_COMPONENT_ACTION)
      if (anAction is RTBarAction) {
        when (anAction) {
          is ExecutorRunToolbarAction -> getChildPreferredSize(it).width
          is RTRunConfiguration -> {
            if (anAction.isStable()) {
              val configWidth = getChildPreferredSize(it).width.toFloat()
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

    runWidgetWidthHelper?.rightSideWidth?.let {
      calculateBoundsToFit(size2Fit, bounds)
    } ?: run {
      super.calculateBounds(size2Fit, bounds)
    }
  }

  private fun calculateBoundsToFit(size2Fit: Dimension, bounds: MutableList<Rectangle>) {
    val right_flexible = mutableListOf<Component>()
    val right_stable = mutableListOf<Component>()
    val flexible = mutableListOf<Component>()

    (0 until componentCount).forEach {
      val actionComponent = getComponent(it) as JComponent
      actionComponent.getClientProperty(RUN_TOOLBAR_COMPONENT_ACTION)?.let {
        if (it is RTBarAction) {
          if (it is RTRunConfiguration) {
            (if (it.isStable()) null else flexible)?.add(actionComponent)
          }
          else {
            when (it.getRightSideType()) {
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
      bounds.clear()
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

        return
      }
      else if (flexible.size == 1) {
        val stablePrefWidth = (0 until componentCount).filter {
          right_stable.contains(getComponent(it))
        }.sumOf { getChildPreferredSize(it).width }


        runWidgetWidthHelper?.configWithArrow?.let { (rightWidth + it - stablePrefWidth).let {
          if (it > 0) it else null
        } } ?.let {
          var offset = 0
          for (i in 0 until componentCount) {
            val d = getChildPreferredSize(i)
            var w = d.width
            val component = getComponent(i)
            if(component == flexible[0]) {
              w = it
            }

            val r = bounds[i]
            r.setBounds(insets.left + offset, insets.top, w, DEFAULT_MINIMUM_BUTTON_SIZE.height)
            offset += w
          }
          return
        } ?: run {
          super.calculateBounds(size2Fit, bounds)
        }
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

        return


      } else {
        super.calculateBounds(size2Fit, bounds)
      }
    } ?: run {
      super.calculateBounds(size2Fit, bounds)
    }
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
