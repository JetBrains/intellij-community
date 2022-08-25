// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.scroll

import javax.swing.BoundedRangeModel
import javax.swing.JScrollBar
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

abstract class BoundedRangeModelThresholdListener private constructor(private val model: BoundedRangeModel)
  : ChangeListener {

  override fun stateChanged(e: ChangeEvent) {
    if (isAtThreshold()) onThresholdReached()
  }

  abstract fun onThresholdReached()

  private fun isAtThreshold(): Boolean {
    val visibleAmount = model.extent
    val value = model.value
    val maximum = model.maximum
    if (maximum == 0) return false
    val scrollFraction = (visibleAmount + value) / maximum.toFloat()
    if (scrollFraction < 0.5) return false
    return true
  }

  companion object {
    fun install(scrollBar: JScrollBar, listener: () -> Unit) {
      scrollBar.model.addChangeListener(object : BoundedRangeModelThresholdListener(scrollBar.model) {
        override fun onThresholdReached() {
          listener()
        }
      })
    }
  }
}