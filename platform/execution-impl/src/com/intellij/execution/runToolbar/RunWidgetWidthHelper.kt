// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus
import kotlin.math.roundToInt

@Service(Service.Level.PROJECT)
internal class RunWidgetWidthHelper(private var project: Project) {
  companion object {
    const val RUN_CONFIG_WIDTH_UNSCALED_MIN = 200
    private const val RUN_CONFIG_WIDTH_UNSCALED_MAX = 1200
    private const val ARROW_WIDTH_UNSCALED = 28

    fun getInstance(project: Project): RunWidgetWidthHelper = project.service()
  }

  private val listeners = mutableListOf<UpdateWidth>()

  fun addListener(listener: UpdateWidth) {
    listeners.add(listener)
  }

  fun removeListener(listener: UpdateWidth) {
    listeners.remove(listener)
  }

  var runConfig: Int = JBUI.scale(RUN_CONFIG_WIDTH_UNSCALED_MIN)
    set(value) {
      if(field == value) return
      val min = JBUI.scale(RUN_CONFIG_WIDTH_UNSCALED_MIN)
      val max = JBUI.scale(RUN_CONFIG_WIDTH_UNSCALED_MAX)

      field = if(value > max) {
        max
      } else if(value < min) {
        min
      } else value

      listeners.forEach { it.updated() }
    }

  val runTarget: Int
    get() {
      return (runConfig / 2.5).roundToInt()
    }

  val arrow: Int
    get() {
      return JBUI.scale(ARROW_WIDTH_UNSCALED)
    }

  val configWithArrow: Int
    get() {
      return JBUI.scale(
        ARROW_WIDTH_UNSCALED) + runConfig
    }

  var runConfigWidth: JBValue.Float? = null
  var rightSideWidth: JBValue.Float? = null

}

@ApiStatus.Internal
interface UpdateWidth {
  fun updated()
}