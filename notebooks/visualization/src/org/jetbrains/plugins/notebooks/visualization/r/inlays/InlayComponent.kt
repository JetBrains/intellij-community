/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import java.awt.*
import javax.swing.JPanel

/** Inlay editor component displaying text output, table data and charts for notebook paragraphs. */
open class InlayComponent : JPanel(BorderLayout()), EditorCustomElementRenderer {

  /** Inlay, associated with this component. Our swing component positioned and sized according inlay. */
  var inlay: Inlay<*>? = null

  private var resizeController: ResizeController? = null

  override fun paint(g: Graphics) {
    // We need this fix with AlphaComposite.SrcOver to resolve problem of black background on transparent images such as icons.
    //ToDo: - We need to make some tests on mac and linux for this, maybe this is applicable only to windows platform.
    //      - And also we need to check this on different Windows versions (definitely we have problems on Windows) .
    //      - Definitely we have problems on new macBook
    val oldComposite = (g as Graphics2D).composite
    g.composite = AlphaComposite.SrcOver
    super<JPanel>.paint(g)
    g.composite = oldComposite
  }

  var resizable: Boolean
    get() {
      return resizeController != null
    }
    set(value) {
      if (value && resizeController == null) {
        resizeController = ResizeController(
          component = this,
          editor = inlay!!.editor,
          deltaSize = ::deltaSize,
        )
        addMouseMotionListener(resizeController)
        addMouseListener(resizeController)
      }
      else if (!value && resizeController != null) {
        removeMouseListener(resizeController)
        removeMouseMotionListener(resizeController)
        resizeController = null
      }
    }

  //region EditorCustomElementRenderer
  override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {

    // Actually bounds will be updated only when they are changed what happens relatively rarely.
    updateComponentBounds(inlay)

    // A try to resolve Code Lens problem, when our inlay paints in strange places.
    //        ApplicationManager.getApplication().invokeLater {
    //            updateComponentBounds(inlay)
    //        }
  }

  /** Updates position and size of linked component. */
  fun updateComponentBounds(inlay: Inlay<*>) {
    inlay.bounds?.let { updateComponentBounds(it) }
  }

  /** Returns width of component. */
  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return InlayDimensions.calculateInlayWidth(inlay.editor as EditorImpl)
  }

  /** Returns height of component. */
  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return size.height
  }

  /** Changes size of component and also calls inlay.update. */
  open fun deltaSize(dx: Int, dy: Int) {

    if ((dx == 0 && dy == 0) /* || size.width + dx < 32 || size.height + dy < 32*/) {
      return
    }

    size = Dimension(size.width + dx, size.height + dy)

    inlay?.update()

    revalidate()
    repaint()
  }
  //endregion

  //region InlayToComponentSynchronizer

  /** Normally this should happens directly after getting inlay with editor.inlayModel.addBlockElement. */
  // And this should only happens once.
  open fun assignInlay(inlay: Inlay<*>) {
    this.inlay = inlay

    // This method force inlay to query the size from us.
    inlay.update()
  }

  /** Fits size and position of component to inlay's size and position. */
  private fun updateComponentBounds(targetRegion: Rectangle) {
    if (bounds == targetRegion) {
      return
    }

    bounds = targetRegion

    revalidate()
    repaint()
  }

  /** Deleted inlay. This component itself should be removed manually (like: comp.parent?.remove(comp)). */
  open fun disposeInlay() {

    if (inlay == null) {
      return
    }

    Disposer.dispose(inlay!!)
    inlay = null
  }
  //endregion
}