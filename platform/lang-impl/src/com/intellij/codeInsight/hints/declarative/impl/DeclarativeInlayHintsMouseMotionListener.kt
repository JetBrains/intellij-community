// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.LightweightHint
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference

@ApiStatus.Internal
class DeclarativeInlayHintsMouseMotionListener : EditorMouseMotionListener {
  private var areaUnderCursor: InlayMouseArea? = null
  private var inlayUnderCursor: WeakReference<Inlay<*>>? = null
  private var inlayKeyListener: DeclarativeInlayHintsKeyListener? = null
  private var ctrlDown = false
  private var hint: LightweightHint? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    val inlay = getInlay(e)
    val renderer = if (inlay == null) null else getRenderer(inlay)
    val mouseArea = if (renderer == null || inlay == null) null else getMouseAreaUnderCursor(inlay, renderer, e.mouseEvent)
    val ctrlDown = isControlDown(e.mouseEvent)

    if (inlay != inlayUnderCursor?.get()) {
      hint?.hide()
      if (renderer != null) {
        // renderer != null implies inlay != null
        val bounds = inlay!!.bounds
        if (bounds != null) {
          val translated = Point(e.mouseEvent.x - bounds.x, e.mouseEvent.y - bounds.y)
          hint = renderer.handleHover(e, translated)
        }
      }
      else {
        hint = null
      }
    }

    val hasMovedToAnotherArea = mouseArea != areaUnderCursor
    val hasCtrlKeyStateChanged = ctrlDown != this.ctrlDown
    if (hasMovedToAnotherArea || hasCtrlKeyStateChanged) {
      val oldEntries = areaUnderCursor?.entries
      if (oldEntries != null && hasMovedToAnotherArea) {
        for (entry in oldEntries) {
          entry.isHoveredWithCtrl = false
        }
      }

      val newEntries = mouseArea?.entries ?: emptyList()
      for (entry in newEntries) {
        entry.isHoveredWithCtrl = ctrlDown
      }

      if (ctrlDown && newEntries.isNotEmpty()) {
        (e.editor as? EditorEx)?.setCustomCursor(DeclarativeInlayHintsMouseMotionListener::class.java, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      }
      else {
        (e.editor as? EditorEx)?.setCustomCursor(DeclarativeInlayHintsMouseMotionListener::class.java, null)
      }

      inlayUnderCursor?.get()?.update()
      inlay?.update()

      areaUnderCursor = mouseArea
      this.ctrlDown = ctrlDown
    }

    if (inlay != inlayUnderCursor?.get()) {
      inlayUnderCursor = inlay?.let { WeakReference(it) }
      inlayKeyListener?.let(Disposer::dispose)
      inlayKeyListener = null

      val editor = inlay?.editor
      if (editor is EditorEx) {
        val listener = DeclarativeInlayHintsKeyListener(editor)
        editor.contentComponent.addKeyListener(listener)
        EditorUtil.disposeWithEditor(editor, listener)
        inlayKeyListener = listener
      }
    }
  }

  private fun isControlDown(e: InputEvent): Boolean = (ClientSystemInfo.isMac() && e.isMetaDown) || e.isControlDown

  private fun getRenderer(inlay: Inlay<*>): DeclarativeInlayRendererBase<*>? {
    val renderer = inlay.renderer
    if (renderer !is DeclarativeInlayRendererBase<*>) return null
    return renderer
  }

  private fun getInlay(e: EditorMouseEvent): Inlay<*>? {
    if (e.isConsumed) return null
    if (e.area != EditorMouseEventArea.EDITING_AREA) return null
    return e.inlay
  }

  private fun getMouseAreaUnderCursor(inlay: Inlay<*>, renderer: DeclarativeInlayRendererBase<*>, event: MouseEvent): InlayMouseArea? {
    val bounds = inlay.bounds ?: return null
    val inlayPoint = Point(bounds.x, bounds.y)
    val translated = Point(event.x - inlayPoint.x, event.y - inlayPoint.y)

    return renderer.getMouseArea(translated)
  }

  private inner class DeclarativeInlayHintsKeyListener(private val editor: EditorEx) : Disposable, KeyAdapter() {
    override fun dispose() {
      editor.contentComponent.removeKeyListener(this)
    }

    override fun keyReleased(e: KeyEvent?) {
      if (e != null && !isControlDown(e)) {
        editor.setCustomCursor(DeclarativeInlayHintsMouseMotionListener::class.java, null)

        val entries = areaUnderCursor?.entries
        if (entries != null) {
          for (entry in entries) {
            entry.isHoveredWithCtrl = false
          }

          inlayUnderCursor?.get()?.update()
        }
      }
    }
  }
}