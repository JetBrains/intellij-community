// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.lang.Integer.min
import javax.swing.JComponent

class NewToolbarBorderLayout : BorderLayout() {
  private var lastTarget: Container? = null
  private val componentListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      layoutContainer(lastTarget)
    }

    override fun componentMoved(e: ComponentEvent?) {
      layoutContainer(lastTarget)
    }
  }

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {

    super.addLayoutComponent(comp, constraints)
    if (comp is JComponent) {
      comp.components.forEach { it.addComponentListener(componentListener) }
    }
    comp?.addComponentListener(componentListener)
  }

  override fun layoutContainer(target: Container?) {
    synchronized(target!!.treeLock) {
      lastTarget = target
      val insets = target.insets
      var top = insets.top
      var bottom = target.height - insets.bottom
      var left = insets.left
      var right = target.width - insets.right
      var c: Component?

      if (getLayoutComponent(EAST).also { c = it } != null) {
        val d = c!!.preferredSize
        var hdiff = 0
        if (target.height > 0 && d.height > 0) {
          hdiff = (target.height - d.height) / 2
        }
        c!!.setSize(c!!.width, bottom - top)
        c!!.setBounds(right - d.width, top + hdiff, d.width, bottom - top)
        right -= d.width + hgap

      }

      if (getLayoutComponent(CENTER).also { c = it } != null) {
        val d = c!!.preferredSize
        var hdiff = 0
        if (target.height > 0 && d.height > 0) {
          hdiff = (target.height - d.height) / 2
        }
        c!!.setBounds(right - c!!.preferredSize.width, top + hdiff, c!!.preferredSize.width, bottom - top)
        right -= d.width + hgap
      }

      if (getLayoutComponent(WEST).also { c = it } != null) {
        val d = c!!.preferredSize
        var hdiff = 0
        if (target.height > 0 && d.height > 0) {
          hdiff = (target.height - d.height) / 2
        }
        c!!.setBounds(left, top + hdiff, min(d.width, right), bottom - top)
        left += d.width + hgap
      }
    }
  }
}