// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(InternalJewelApi::class)
class GotItTooltipBalloonPositionProviderTest {
    // anchorBounds: left=100, top=200, right=300, bottom=250 → width=200, height=50
    private val anchorBounds = IntRect(left = 100, top = 200, right = 300, bottom = 250)
    private val arrowOffset = 24
    private val popupSize = IntSize(width = 280, height = 120)

    @Test
    fun `BELOW BottomCenter positions popup at anchorY with arrowOffset applied to x`() {
        // BottomCenter.align on 200×50 → (100, 50) → anchorX = 200, anchorY = 250
        val result =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.BELOW,
                anchor = Alignment.BottomCenter,
                arrowOffsetPx = arrowOffset,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popupSize,
            )
        assertEquals(IntOffset(x = 176, y = 250), result)
    }

    @Test
    fun `ABOVE BottomCenter positions popup above anchor`() {
        // anchorX = 200, anchorY = 250 → y = 250 - 120 = 130
        val result =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.ABOVE,
                anchor = Alignment.BottomCenter,
                arrowOffsetPx = arrowOffset,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popupSize,
            )
        assertEquals(IntOffset(x = 176, y = 130), result)
    }

    @Test
    fun `START CenterStart positions popup to the left`() {
        // CenterStart.align on 200×50 → (0, 25) → anchorX = 100, anchorY = 225
        // x = 100 - 280 = -180, y = 225 - 24 = 201
        val result =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.START,
                anchor = Alignment.CenterStart,
                arrowOffsetPx = arrowOffset,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popupSize,
            )
        assertEquals(IntOffset(x = -180, y = 201), result)
    }

    @Test
    fun `END CenterEnd positions popup to the right`() {
        // CenterEnd.align on 200×50 → (200, 25) → anchorX = 300, anchorY = 225
        // x = 300, y = 225 - 24 = 201
        val result =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.END,
                anchor = Alignment.CenterEnd,
                arrowOffsetPx = arrowOffset,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popupSize,
            )
        assertEquals(IntOffset(x = 300, y = 201), result)
    }

    @Test
    fun `BELOW with TopCenter anchor uses top of anchor as Y`() {
        // TopCenter.align on 200×50 → (100, 0) → anchorX = 200, anchorY = 200
        val result =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.BELOW,
                anchor = Alignment.TopCenter,
                arrowOffsetPx = arrowOffset,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popupSize,
            )
        assertEquals(IntOffset(x = 176, y = 200), result)
    }

    @Test
    fun `BELOW with CenterStart anchor and arrowOffset equal to anchorX from left gives x of zero`() {
        // CenterStart.align on 200×50 → (0, 25) → anchorX = 100, anchorY = 225
        // arrowOffset = anchorX = 100 → x = 100 - 100 = 0
        val result =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.BELOW,
                anchor = Alignment.CenterStart,
                arrowOffsetPx = 100,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popupSize,
            )
        assertEquals(IntOffset(x = 0, y = 225), result)
    }

    @Test
    fun `RTL layout is handled by Alignment align`() {
        // TopEnd in LTR → x=200 → anchorX=300; TopEnd in RTL → x=0 → anchorX=100
        val ltrResult =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.BELOW,
                anchor = Alignment.TopEnd,
                arrowOffsetPx = arrowOffset,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popupSize,
            )
        val rtlResult =
            calculateBalloonPosition(
                gotItBalloonPosition = GotItBalloonPosition.BELOW,
                anchor = Alignment.TopEnd,
                arrowOffsetPx = arrowOffset,
                anchorBounds = anchorBounds,
                layoutDirection = LayoutDirection.Rtl,
                popupContentSize = popupSize,
            )
        // LTR: anchorX = 100+200=300, y=200 → IntOffset(276, 200)
        assertEquals(IntOffset(x = 276, y = 200), ltrResult)
        // RTL: anchorX = 100+0=100, y=200 → IntOffset(76, 200)
        assertEquals(IntOffset(x = 76, y = 200), rtlResult)
    }
}
