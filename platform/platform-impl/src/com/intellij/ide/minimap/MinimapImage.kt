// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.util.Alarm
import com.intellij.util.ui.ImageUtil
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil

/**
 * A rendered minimap of a document.
 */
class MinimapImage(parentDisposable: Disposable) {

  companion object {
    private const val UPDATE_DELAY_MILLIS = 200
    private const val bufferUnscaledHeight = 500
  }

  // target small image drawn on minimap
  var preview: BufferedImage? = null
  private var imgHeight = -1
  private var imgWidth = -1
  private var height: Int = -1  // pixels

  private var contentHeight = -1
  private var contentVisibleWidth = -1

  private var bufferUnscaled: BufferedImage? = null
  private var buHeight = -1
  private var buWidth = -1

  private var lastWidth = -1

  private val alarm = Alarm(parentDisposable)

  var onImageReady: (() -> Unit)? = null

  private fun updateImage(editor: Editor) {

    if (buWidth != contentVisibleWidth || buHeight != bufferUnscaledHeight) {
      bufferUnscaled = ImageUtil.createImage(contentVisibleWidth, bufferUnscaledHeight, BufferedImage.TYPE_INT_ARGB)
      buHeight = bufferUnscaledHeight
      buWidth = contentVisibleWidth
    }

    val bufferUnscaled = this.bufferUnscaled!!

    val blocks = ceil(contentHeight.toDouble() / buHeight).toInt()

    val g = bufferUnscaled.createGraphics()

    val g2d = preview!!.createGraphics()

    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

    val minimapHeightEx = (buHeight * imgWidth.toDouble() / buWidth).toInt()

    for (i in 0 until blocks) {
      editor.contentComponent.print(g)

      g2d.drawImage(bufferUnscaled,
                    0, i * minimapHeightEx, imgWidth, (i + 1) * minimapHeightEx,
                    0, 0, bufferUnscaled.width, bufferUnscaled.height,
                    null)

      g.translate(0, -buHeight)
    }

    g.dispose()

    var fillHeight = blocks * buHeight - contentHeight
    if (fillHeight > 0) {
      fillHeight = (fillHeight * imgWidth.toDouble() / buWidth / g2d.transform.scaleY).toInt()
      g2d.color = editor.contentComponent.background
      g2d.fillRect(0, imgHeight - fillHeight, imgWidth, fillHeight)
    }

    g2d.dispose()
  }

  fun update(editor: Editor, visibleHeight: Int, visibleWidth: Int, minimapHeight: Int, force: Boolean = false) {
    if (alarm.activeRequestCount != 0) {
      alarm.cancelAllRequests()
    }

    if (minimapHeight <= 0 || visibleHeight <= 0 || visibleWidth <= 0) {
      return
    }

    if (force) {
      lastWidth = -1
    }

    alarm.addRequest({ innerUpdate(editor, visibleHeight, visibleWidth, minimapHeight) }, UPDATE_DELAY_MILLIS)
  }

  private fun innerUpdate(editor: Editor, visibleHeight: Int, visibleWidth: Int, minimapHeight: Int) {

    val state = MinimapSettings.getInstance().state

    if (contentHeight == visibleHeight &&
        contentVisibleWidth == visibleWidth &&
        lastWidth == state.width) {
      return
    }

    contentHeight = visibleHeight
    contentVisibleWidth = visibleWidth

    lastWidth = state.width

    if (contentHeight <= 0 || contentVisibleWidth <= 0 || state.width <= 0) {
      return
    }

    height = minimapHeight

    if (imgWidth != state.width || imgHeight != height) {
      preview = ImageUtil.createImage(state.width, height, BufferedImage.TYPE_INT_ARGB)
      imgHeight = height
      imgWidth = state.width
    }

    updateImage(editor)

    onImageReady?.invoke()
  }
}