// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.codeInsight.hints.ContentKey
import com.intellij.codeInsight.hints.InlayKey
import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent

/**
 * This class has some unavoidable problems: update happens on every pass, update is recursive and may be even unnecessary.
 * New classes must not use this implementation!
 */
open class RecursivelyUpdatingRootPresentation(private var current: InlayPresentation) : BasePresentation(), RootInlayPresentation<InlayPresentation> {
  private var listener = MyPresentationListener()
  init {
    current.addListener(listener)
  }

  override fun update(
    newPresentationContent: InlayPresentation,
    editor: Editor,
    factory: InlayPresentationFactory
  ): Boolean {
    val previous = current
    current.removeListener(listener)
    current = newPresentationContent
    listener = MyPresentationListener()
    current.addListener(listener)
    val previousDimension = Dimension(previous.width, previous.height)
    val updated = newPresentationContent.updateState(previous)
    if (updated) {
      fireContentChanged(Rectangle(0, 0, width, height))
      val currentDimension = Dimension(width, height)
      if (previousDimension != currentDimension) {
        fireSizeChanged(previousDimension, currentDimension)
      }
    }
    return updated
  }

  override val content: InlayPresentation
    get() = current

  override val key: ContentKey<InlayPresentation>
    get() = KEY

  // All other members are just delegation to underlying presentation

  override val width: Int
    get() = this.current.width
  override val height: Int
    get() = this.current.height

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    this.current.paint(g, attributes)
  }

  override fun toString(): String {
    return this.current.toString()
  }

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    this.current.mouseClicked(event, translated)
  }

  override fun mousePressed(event: MouseEvent, translated: Point) {
    this.current.mousePressed(event, translated)
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    this.current.mouseMoved(event, translated)
  }

  override fun mouseExited() {
    this.current.mouseExited()
  }

  companion object {
    private val KEY: ContentKey<InlayPresentation> = InlayKey<Any, InlayPresentation>("recursive.update.root")
  }

  inner class MyPresentationListener : PresentationListener {
    override fun contentChanged(area: Rectangle) {
      this@RecursivelyUpdatingRootPresentation.fireContentChanged(area)
    }

    override fun sizeChanged(previous: Dimension, current: Dimension) {
      this@RecursivelyUpdatingRootPresentation.fireSizeChanged(previous, current)
    }
  }
}