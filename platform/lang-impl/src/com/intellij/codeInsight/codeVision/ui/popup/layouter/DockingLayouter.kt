// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.popup.layouter

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.ui.ScreenUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

@ApiStatus.Internal
class DockingLayouter(lifetime: Lifetime,
                      private val anchor: AnchoringRect,
                      dispositions: List<Anchoring2D>,
                      val project: Project,
                      private val padding: Int = 0,
                      private val promoteRecentlyUsedDisposition: Boolean = true) {
  val layout: IProperty<LayoutResult?> = Property(null)
  val size: IOptProperty<Dimension> = OptProperty()
  private val myDispositions: MutableList<Anchoring2D> = ArrayList(dispositions)

  init {
    layout.adviseNotNull(lifetime) { result ->

      if (promoteRecentlyUsedDisposition) {
        val promoted = result.disposition
        myDispositions.remove(promoted)
        myDispositions.add(0, promoted)
      }
    }
    size.view(lifetime) { lt, sz ->
      anchor.rectangle.advise(lt) { rect ->
        updateLayout(sz, rect)
      }
    }
  }

  private fun updateLayout(size: Dimension, anchorRect: Rectangle?) {
    if (anchorRect == null) {
      layout.set(null)
      return
    }
    val screenRect = getScreenRectangle(anchorRect.location)
    layout.set(RectangleDocker(anchorRect, size, myDispositions, screenRect, padding).layout())
  }

  private fun getScreenRectangle(point: Point): Rectangle {
    if (!SystemInfo.isWindows)
      return ScreenUtil.getScreenRectangle(point)

    val instance = IdeFocusManager.findInstance()
    val lastFocusedFrame = instance.lastFocusedFrame
    if (lastFocusedFrame is IdeFrameEx && lastFocusedFrame.isInFullScreen) {
      val device = ScreenUtil.getScreenDevice(Rectangle(point, Dimension(1, 1)))
      if (device != null && device.defaultConfiguration != null)
        return device.defaultConfiguration.bounds
    }

    return ScreenUtil.getScreenRectangle(point)
  }
}