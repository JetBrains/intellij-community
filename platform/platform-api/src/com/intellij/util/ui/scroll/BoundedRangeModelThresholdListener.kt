// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.scroll

import javax.swing.BoundedRangeModel
import javax.swing.JScrollBar
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

abstract class BoundedRangeModelThresholdListener(
  private val model: BoundedRangeModel,
  private val thresholdPercent: Float
) : ChangeListener {

  init {
    require(thresholdPercent > 0 && thresholdPercent < 1) { "Threshold should be a value greater than 0 and lesser than 1" }
  }

  override fun stateChanged(e: ChangeEvent) {
    if (model.valueIsAdjusting) return
    if (isAtThreshold()) {
      onThresholdReached()
    }
  }

  abstract fun onThresholdReached()

  private fun isAtThreshold(): Boolean {
    val visibleAmount = model.extent
    val value = model.value
    val maximum = model.maximum
    if (maximum == 0) return false
    val scrollFraction = (visibleAmount + value) / maximum.toFloat()
    if (scrollFraction < thresholdPercent) return false
    return true
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun install(scrollBar: JScrollBar, threshold: Float = 0.5f, listener: () -> Unit) {
      scrollBar.model.addChangeListener(object : BoundedRangeModelThresholdListener(scrollBar.model, threshold) {
        override fun onThresholdReached() = listener()
      })
    }
  }
}