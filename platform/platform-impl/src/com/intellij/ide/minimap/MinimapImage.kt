// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.concurrency.JobScheduler
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.ImageUtil
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/**
 * A rendered minimap of a document.
 */
class MinimapImage {

  companion object {
    private const val UPDATE_DELAY_MILLIS = 200L
    private const val BUFFER_UNSCALED_HEIGHT = 500
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

  var onImageReady: (() -> Unit)? = null

  private fun updateImage(editor: Editor) {

    if (buWidth != contentVisibleWidth || buHeight != BUFFER_UNSCALED_HEIGHT) {
      bufferUnscaled = ImageUtil.createImage(contentVisibleWidth, BUFFER_UNSCALED_HEIGHT, BufferedImage.TYPE_INT_ARGB)
      buHeight = BUFFER_UNSCALED_HEIGHT
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

    // val downscaleSteps = min((bufferUnscaled.width - imgWidth) / 100, 5)

    //val editorImpl =  (editor.contentComponent as EditorComponentImpl).editor
    //val editorView = editorImpl.javaClass.getDeclaredField("myView").apply{ isAccessible = true}.get(editorImpl)
    //val editorPainter = editorView.javaClass.getDeclaredField("myPainter").apply { isAccessible = true }.get(editorView) as EditorPainter
    for (i in 0 until blocks) {

      //setFlag(JComponent.IS_PRINTING, true)
      //firePropertyChange("paintingForPrint", false, true)
      //try {
      //  editorPainter.paint(g)
      //}
      //finally {
      //  setFlag(JComponent.IS_PRINTING, false)
      //  firePropertyChange("paintingForPrint", true, false)
      //}

      invokeAndWaitIfNeeded {
        editor.contentComponent.print(g)
      }

      //   if (downscaleSteps == 0)
      // {
      // Direct one-pass copy without progressive downscale
      g2d.drawImage(bufferUnscaled,
                    0, i * minimapHeightEx, imgWidth, (i + 1) * minimapHeightEx,
                    0, 0, bufferUnscaled.width, bufferUnscaled.height,
                    null)
      //   }
      //else {
      //  progressiveDownscale(bufferUnscaled, imgWidth, minimapHeightEx, downscaleSteps)
      //
      //  g2d.drawImage(bufferUnscaled,
      //                0, i * minimapHeightEx, imgWidth, (i + 1) * minimapHeightEx,
      //                0, 0, imgWidth, minimapHeightEx,
      //                null)
      //}
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

  private fun progressiveDownscale(image: BufferedImage, finalWidth: Int, finalHeight: Int, steps: Int) {
    val g2d = image.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

    var imageWidth = image.width
    var imageHeight = image.height

    val deltaWidth = (imageWidth - finalWidth) / steps
    val deltaHeight = (imageHeight - finalHeight) / steps

    for (i in 0 until steps) {
      g2d.drawImage(image, 0, 0, imageWidth - deltaWidth, imageHeight - deltaHeight, 0, 0, imageWidth, imageHeight, null)
      imageWidth -= deltaWidth
      imageHeight -= deltaHeight
    }
  }

  private val scheduled = AtomicBoolean()

  fun update(editor: Editor, visibleHeight: Int, visibleWidth: Int, minimapHeight: Int, force: Boolean = false) {

    if (minimapHeight <= 0 || visibleHeight <= 0 || visibleWidth <= 0) {
      return
    }

    if (force) {
      lastWidth = -1
    }

    if (scheduled.compareAndSet(false, true)) {
      JobScheduler.getScheduler().schedule({
                                             try {
                                               innerUpdate(editor, visibleHeight, visibleWidth, minimapHeight)
                                             }
                                             finally {
                                               scheduled.set(false)
                                             }
                                           }, UPDATE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    }
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