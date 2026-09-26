// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.RemoteDesktopService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.JBValue.UIInteger
import com.intellij.util.ui.MacUIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D

object DrawUtil {

  private val COMPONENT_ARC: JBValue = UIInteger("Component.arc", 5)

  @JvmStatic
  fun isSimplifiedUI(): Boolean {
    // SimplifiedUiTracker listens to each source below
    return `is`("ui.simplified", false) ||
           RemoteDesktopService.isRemoteSession() ||
           PowerSaveMode.isEnabled()
  }

  /**
   * Calls [listener] once, and again after each change of [isSimplifiedUI].
   * The subscription ends when [parent] is disposed.
   *
   * The platform posts the first call. Read [isSimplifiedUI] when you need the value at once.
   *
   * Call this method after the application loads its components.
   */
  @ApiStatus.Experimental
  @JvmStatic
  fun subscribeSimplifiedUI(parent: Disposable, listener: SimplifiedUiListener) {
    LoadingState.COMPONENTS_LOADED.checkOccurred()
    service<SimplifiedUiTracker>().subscribe(parent, listener)
  }

  @ApiStatus.Experimental
  fun interface SimplifiedUiListener {
    /**
     * Read [DrawUtil.isSimplifiedUI] to get the new value.
     * The platform serializes the calls, but does not specify the thread.
     */
    fun simplifiedUiChanged()
  }

  @JvmStatic
  @ApiStatus.Internal
  fun setupRenderingHints(g: Graphics2D) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                       if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
  }

  @JvmStatic
  @ApiStatus.Internal
  fun fillRoundedRectangle(g: Graphics, rect: Rectangle, color: Color, arc: Float = COMPONENT_ARC.float) {
    if (rect.width <= 0 || rect.height <= 0) {
      return
    }

    val g2 = g.create() as Graphics2D

    try {
      setupRenderingHints(g2)

      val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
      border.append(RoundRectangle2D.Float(0f, 0f, rect.width.toFloat(), rect.height.toFloat(), arc, arc), false)
      g2.translate(rect.x, rect.y)
      g2.color = color
      g2.fill(border)
    }
    finally {
      g2.dispose()
    }
  }
}
