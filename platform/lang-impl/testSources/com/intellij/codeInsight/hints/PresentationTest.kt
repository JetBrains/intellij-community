// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.codeInsight.hints.presentation.SpacePresentation
import junit.framework.TestCase
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JPanel

class PresentationTest : TestCase() {
  private fun click(x: Int, y: Int) : MouseEvent {
    return MouseEvent(JPanel(), 0, 0, 0, x, y, 0, true, 0)
  }

  fun testSequenceDimension() {
    val presentation = SequencePresentation(listOf(SpacePresentation(10, 8), SpacePresentation(30, 5)))
    assertEquals(40, presentation.width)
    assertEquals(8, presentation.height)
  }

  fun testSequenceMouseClick() {
    val left = TriggerPresentation(SpacePresentation(50, 10), 40, 5)
    val presentation = SequencePresentation(listOf(left, SpacePresentation(30, 10)))
    presentation.mouseClicked(click(40, 5), Point(1, 2))
    isTriggered(left)
  }

  fun testSequenceMouseClick2() {
    val left = SpacePresentation(50, 10)
    val right = TriggerPresentation(SpacePresentation(30, 10), 10, 5)
    val presentation = SequencePresentation(listOf(left, right))
    presentation.mouseClicked(click(60, 5), Point(1, 2))
    isTriggered(right)
  }

  fun testSequenceMouseClickOutside() {
    val left = TriggerPresentation(SpacePresentation(50, 10), 10, 5)
    val right = TriggerPresentation(SpacePresentation(30, 10), 10, 5)
    val presentation = SequencePresentation(listOf(left, right))
    presentation.mouseClicked(click(100, 5), Point(1, 2))
    assertFalse("Expected presentation is not clicked", left.triggered)
    assertFalse("Expected presentation is not clicked", right.triggered)
  }

  private fun isTriggered(presentation: TriggerPresentation) {
    assertTrue("Expected presentation is not clicked", presentation.triggered)
  }

  private class TriggerPresentation(
    val presentation: InlayPresentation,
    val expectedX: Int,
    val expectedY : Int
  ):  InlayPresentation by presentation {
    var triggered = false

    override fun mouseClicked(e: MouseEvent, editorPoint: Point) {
      assertEquals(expectedX, e.x)
      assertEquals(expectedY, e.y)
      triggered = true
      super.mouseClicked(e, editorPoint)
    }
  }
}