// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.components.panels.NonOpaquePanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics

@ApiStatus.Internal
open class HwFacadeNonOpaquePanel : NonOpaquePanel() {
  private val hwFacadeHelper = HwFacadeHelper.create(this)

  override fun addNotify() {
    super.addNotify()
    hwFacadeHelper.addNotify()
  }

  override fun removeNotify() {
    super.removeNotify()
    hwFacadeHelper.removeNotify()
  }

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun show() {
    super.show()
    hwFacadeHelper.show()
  }

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun hide() {
    super.hide()
    hwFacadeHelper.hide()
  }

  override fun paint(g: Graphics) {
    hwFacadeHelper.paint(g) { super.paint(it) }
  }
}
