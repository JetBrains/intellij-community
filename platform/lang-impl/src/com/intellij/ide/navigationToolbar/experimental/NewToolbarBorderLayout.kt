// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.experimental

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container

internal class NewToolbarBorderLayout : BorderLayout() {
  private var lastTarget: Container? = null

  override fun layoutContainer(target: Container?) {
    synchronized(target!!.treeLock) {
      lastTarget = target
      val insets = target.insets
      val top = insets.top
      val bottom = target.height - insets.bottom
      var left = insets.left
      var right = target.width - insets.right
      var c: Component?

      if (getLayoutComponent(EAST).also { c = it } != null) {
        val d = c!!.preferredSize
        var heightDiff = 0
        if (target.height > 0 && d.height > 0) {
          heightDiff = (target.height - d.height) / 2
        }
        c!!.setSize(c!!.width, bottom - top)
        c!!.setBounds(right - d.width, top + heightDiff, d.width, bottom - top)
        right -= d.width + hgap

      }

      if (getLayoutComponent(CENTER).also { c = it } != null) {
        val d = c!!.preferredSize
        var heightDiff = 0
        if (target.height > 0 && d.height > 0) {
          heightDiff = (target.height - d.height) / 2
        }
        c!!.setBounds(right - c!!.preferredSize.width, top + heightDiff, c!!.preferredSize.width, bottom - top)
        right -= d.width + hgap
      }

      if (getLayoutComponent(WEST).also { c = it } != null) {
        val d = c!!.preferredSize
        var heightDiff = 0
        if (target.height > 0 && d.height > 0) {
          heightDiff = (target.height - d.height) / 2
        }
        if(right < d.width) {
          left -= d.width - right
        }
        c!!.setBounds(left, top + heightDiff, d.width, bottom - top)
        left += d.width + hgap
      }
    }
  }
}