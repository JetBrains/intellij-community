// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.BadLocationException
import kotlin.math.ceil

interface DocRenderLinkActivationHandler {
  fun activateLink(event: HyperlinkEvent, renderer: DocRenderer)

  fun getLocation(event: HyperlinkEvent): Rectangle2D? {
    val element = event.sourceElement ?: return null
    val location = try {
      (event.source as JEditorPane).modelToView2D(element.startOffset)
    }
    catch (ignored: BadLocationException) {
      null
    }
    return location
  }

  val isGotoDeclarationEvent: Boolean
    get() {
      val keymapManager = KeymapManager.getInstance() ?: return false
      val event = IdeEventQueue.getInstance().trueCurrentEvent as? MouseEvent ?: return false
      val mouseShortcut = KeymapUtil.createMouseShortcut(event)
      return keymapManager.activeKeymap.getActionIds(mouseShortcut).contains(IdeActions.ACTION_GOTO_DECLARATION)
    }

  fun popupPosition(linkLocationWithinInlay: Rectangle2D, renderer: DocRenderer): Point {
    val foldRegion = renderer.item.foldRegion ?: return Point(0, 0)
    val rendererPosition = foldRegion.location ?: return Point(0, 0)
    val relativeBounds = renderer.getEditorPaneBoundsWithinRenderer(foldRegion.widthInPixels, foldRegion.heightInPixels)
    return Point(
      rendererPosition.x + relativeBounds.x + linkLocationWithinInlay.x.toInt(),
      rendererPosition.y + relativeBounds.y + ceil(linkLocationWithinInlay.maxY).toInt()
    )
  }
}