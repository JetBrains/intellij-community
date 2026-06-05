// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.util.function.Consumer
import javax.swing.JComponent

/**
 * Provides an optional heavyweight facade for lightweight popup/dialog components.
 * The platform default is a no-op; JCEF installs an implementation that can overlap windowed browser components.
 */
@ApiStatus.Internal
abstract class HwFacadeHelper {
  abstract fun addNotify()

  abstract fun removeNotify()

  abstract fun show()

  abstract fun hide()

  abstract fun paint(g: Graphics, targetPaint: Consumer<in Graphics>)

  companion object {
    private val EP_NAME = ExtensionPointName.create<HwFacadeProvider>("com.intellij.hwFacadeProvider")

    @JvmStatic
    fun create(target: JComponent): HwFacadeHelper {
      val provider = EP_NAME.findFirstSafe { it.isAvailable() }
      return provider?.create(target) ?: NoOpHwFacadeHelper
    }
  }

  private object NoOpHwFacadeHelper : HwFacadeHelper() {
    override fun addNotify() {
    }

    override fun removeNotify() {
    }

    override fun show() {
    }

    override fun hide() {
    }

    override fun paint(g: Graphics, targetPaint: Consumer<in Graphics>) {
      targetPaint.accept(g)
    }
  }
}
