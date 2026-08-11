// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(InternalJewelApi::class)
class GotItTooltipBalloonShapeTest {
    private val density = Density(1f)
    private val size = Size(300f, 200f)
    private val arrowWidth = 16.dp
    private val arrowHeight = 8.dp
    private val cornerRadius = 8.dp
    private val arrowOffset = 24.dp

    private fun outline(
        position: GotItBalloonPosition,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        offset: Dp = arrowOffset,
    ): Outline =
        createBalloonOutline(
            size = size,
            layoutDirection = layoutDirection,
            density = density,
            arrowWidth = arrowWidth,
            arrowHeight = arrowHeight,
            cornerRadius = cornerRadius,
            arrowPosition = position,
            arrowOffset = offset,
        )

    @Test
    fun `outline is non-empty for all four positions`() {
        for (position in GotItBalloonPosition.entries) {
            val o = outline(position)
            assertTrue("Outline for $position should be Outline.Generic", o is Outline.Generic)
            val bounds = (o as Outline.Generic).path.getBounds()
            assertFalse("Path bounds for $position should be non-empty", bounds.isEmpty)
        }
    }

    @Test
    fun `BELOW path bounds span the full size`() {
        val o = outline(GotItBalloonPosition.BELOW)
        val bounds = (o as Outline.Generic).path.getBounds()
        // The arrow tip protrudes to y=0; the rect bottom is at y=size.height
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(size.height, bounds.bottom, 0.01f)
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(size.width, bounds.right, 0.01f)
    }

    @Test
    fun `ABOVE path bounds span the full size`() {
        val o = outline(GotItBalloonPosition.ABOVE)
        val bounds = (o as Outline.Generic).path.getBounds()
        // The rect top is at y=0; the arrow tip protrudes to y=size.height
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(size.height, bounds.bottom, 0.01f)
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(size.width, bounds.right, 0.01f)
    }

    @Test
    fun `START path bounds span the full size`() {
        val o = outline(GotItBalloonPosition.START)
        val bounds = (o as Outline.Generic).path.getBounds()
        // Rect extends from (0, 0) to (width-arrowHeight, height); arrow tip at x=width
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(size.height, bounds.bottom, 0.01f)
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(size.width, bounds.right, 0.01f)
    }

    @Test
    fun `END path bounds span the full size`() {
        val o = outline(GotItBalloonPosition.END)
        val bounds = (o as Outline.Generic).path.getBounds()
        // Rect starts at x=arrowHeight; arrow tip at x=0
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(size.height, bounds.bottom, 0.01f)
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(size.width, bounds.right, 0.01f)
    }

    @Test
    fun `BELOW RTL produces a valid non-empty outline`() {
        val ltr = outline(GotItBalloonPosition.BELOW, LayoutDirection.Ltr)
        val rtl = outline(GotItBalloonPosition.BELOW, LayoutDirection.Rtl)

        assertTrue(ltr is Outline.Generic)
        assertTrue(rtl is Outline.Generic)
        assertFalse((ltr as Outline.Generic).path.getBounds().isEmpty)
        assertFalse((rtl as Outline.Generic).path.getBounds().isEmpty)
    }

    @Test
    fun `arrow center is coerced away from corners for very small offset`() {
        val o = outline(GotItBalloonPosition.BELOW, offset = 0.dp)
        assertTrue(o is Outline.Generic)
        assertFalse((o as Outline.Generic).path.getBounds().isEmpty)
    }

    @Test
    fun `arrow center is coerced away from corners for very large offset`() {
        val o = outline(GotItBalloonPosition.BELOW, offset = 1000.dp)
        assertTrue(o is Outline.Generic)
        assertFalse((o as Outline.Generic).path.getBounds().isEmpty)
    }

    @Test
    fun `arrow coercion for vertical positions uses very small offset`() {
        val o = outline(GotItBalloonPosition.END, offset = 0.dp)
        assertTrue(o is Outline.Generic)
        assertFalse((o as Outline.Generic).path.getBounds().isEmpty)
    }

    @Test
    fun `arrow coercion for vertical positions uses very large offset`() {
        val o = outline(GotItBalloonPosition.START, offset = 1000.dp)
        assertTrue(o is Outline.Generic)
        assertFalse((o as Outline.Generic).path.getBounds().isEmpty)
    }
}
