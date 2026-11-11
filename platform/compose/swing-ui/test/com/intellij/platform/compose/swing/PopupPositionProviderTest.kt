// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.junit.jupiter.api.Test
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.assertEquals

/** Where a popup window goes against the component it is shown for, and what the host passes to `JBPopup.setLocation` for it. */
class PopupPositionProviderTest {

  @Test
  fun aPopupAboveTheEndOfItsAnchorKeepsTheEndEdgesInLine() {
    val bounds = popupWindowBounds(ABOVE_END, ANCHOR, SCREEN, ComponentOrientation.LEFT_TO_RIGHT, Dimension(450, 300))

    assertEquals(Rectangle(550, 700, 450, 300), bounds)
  }

  @Test
  fun aRightToLeftAnchorMirrorsTheAlignment() {
    val bounds = popupWindowBounds(ABOVE_END, ANCHOR, SCREEN, ComponentOrientation.RIGHT_TO_LEFT, Dimension(450, 300))

    assertEquals(Rectangle(900, 700, 450, 300), bounds)
  }

  @Test
  fun aRightToLeftAnchorMirrorsTheHorizontalOffset() {
    val provider = AnchorAlignmentPositionProvider(Alignment.BottomStart, Alignment.TopStart, offset = Point(10, 5))

    assertEquals(Point(910, 1025), provider.calculatePosition(ANCHOR, SCREEN, ComponentOrientation.LEFT_TO_RIGHT, Dimension(100, 100)))
    assertEquals(Point(890, 1025), provider.calculatePosition(ANCHOR, SCREEN, ComponentOrientation.RIGHT_TO_LEFT, Dimension(100, 100)))
  }

  @Test
  fun aPopupThatWouldLeaveTheScreenMovesBackOntoIt() {
    val bounds = popupWindowBounds(BELOW_START, ANCHOR, SCREEN, ComponentOrientation.LEFT_TO_RIGHT, Dimension(450, 300))

    assertEquals(Rectangle(900, 780, 450, 300), bounds)
  }

  @Test
  fun aScreenLeftOfTheMainOneKeepsItsNegativeOrigin() {
    val screen = Rectangle(-1920, 0, 1920, 1080)
    val anchor = Rectangle(-1000, 1000, 100, 20)

    val bounds = popupWindowBounds(ABOVE_END, anchor, screen, ComponentOrientation.LEFT_TO_RIGHT, Dimension(450, 300))

    assertEquals(Rectangle(-1350, 700, 450, 300), bounds)
  }

  @Test
  fun theLocationArgumentAddsTheInsetsThatSetLocationTakesOff() {
    val insets = Insets(3, 2, 3, 2)
    val window = Point(100, 100)

    val argument = popupLocationArgument(Point(500, 400), window, reported = Point(102, 103), insets)

    assertEquals(Point(502, 403), argument)
  }

  @Test
  fun theLocationArgumentAddsTheHeaderOffsetOfAPopupLocatedByItsContent() {
    val insets = Insets(3, 2, 3, 2)
    val window = Point(100, 100)
    val header = 20

    val argument = popupLocationArgument(Point(500, 400), window, reported = Point(102, 103 - header), insets)

    // setLocation takes off the insets and the header offset again, which puts the window at the target.
    assertEquals(Point(500, 400), Point(argument.x - insets.left, argument.y - insets.top - header))
  }

  private companion object {
    val SCREEN = Rectangle(0, 0, 1920, 1080)
    val ANCHOR = Rectangle(900, 1000, 100, 20)
    val ABOVE_END = AnchorAlignmentPositionProvider(Alignment.TopEnd, Alignment.BottomEnd)
    val BELOW_START = AnchorAlignmentPositionProvider(Alignment.BottomStart, Alignment.TopStart)
  }
}
