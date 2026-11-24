// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

public class AnchorHorizontalMenuPositionProviderTest {
    @Test
    public fun `ltr right and left placement aligns parent and child menu edges across fractional densities`() {
        for (scale in listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)) {
            val density = Density(scale)
            val horizontalInset = with(density) { 2.dp.roundToPx() }
            val verticalInset = with(density) { 7.dp.roundToPx() }
            val parentWidth = with(density) { 100.dp.roundToPx() }
            val childSize = IntSize(width = with(density) { 90.dp.roundToPx() }, height = 60)
            val windowSize = IntSize(width = 1200, height = 800)

            val provider =
                AnchorHorizontalMenuPositionProvider(
                    contentOffset = DpOffset(2.dp, (-7).dp),
                    contentMargin = PaddingValues(),
                    alignment = Alignment.Top,
                    density = density,
                )

            val parentTop = 100
            val parentLeft = 200
            val rightOpenAnchor =
                IntRect(
                    left = parentLeft + horizontalInset,
                    top = parentTop + verticalInset,
                    right = parentLeft + parentWidth - horizontalInset,
                    bottom = parentTop + verticalInset + 28,
                )
            val rightPos = provider.calculatePosition(rightOpenAnchor, windowSize, LayoutDirection.Ltr, childSize)
            assertEquals("LTR right-open X mismatch at scale=$scale", parentLeft + parentWidth, rightPos.x)
            assertEquals("LTR right-open Y mismatch at scale=$scale", parentTop, rightPos.y)

            val flipParentLeft = windowSize.width - parentWidth - 20
            val leftFlipAnchor =
                IntRect(
                    left = flipParentLeft + horizontalInset,
                    top = parentTop + verticalInset,
                    right = flipParentLeft + parentWidth - horizontalInset,
                    bottom = parentTop + verticalInset + 28,
                )
            val leftPos = provider.calculatePosition(leftFlipAnchor, windowSize, LayoutDirection.Ltr, childSize)
            assertEquals("LTR left-flip X mismatch at scale=$scale", flipParentLeft - childSize.width, leftPos.x)
            assertEquals("LTR left-flip Y mismatch at scale=$scale", parentTop, leftPos.y)
        }
    }

    @Test
    public fun `rtl horizontal offset does not invert sign into parent overlap`() {
        val density = Density(2.0f)
        val horizontalInset = with(density) { 2.dp.roundToPx() }
        val verticalInset = with(density) { 7.dp.roundToPx() }
        val parentWidth = with(density) { 100.dp.roundToPx() }
        val childSize = IntSize(width = with(density) { 90.dp.roundToPx() }, height = 60)
        val windowSize = IntSize(width = 1200, height = 800)

        val provider =
            AnchorHorizontalMenuPositionProvider(
                contentOffset = DpOffset(2.dp, (-7).dp),
                contentMargin = PaddingValues(),
                alignment = Alignment.Top,
                density = density,
            )

        val parentTop = 100
        val parentLeft = 200
        val rightOpenAnchor =
            IntRect(
                left = parentLeft + horizontalInset,
                top = parentTop + verticalInset,
                right = parentLeft + parentWidth - horizontalInset,
                bottom = parentTop + verticalInset + 28,
            )
        val rightPos = provider.calculatePosition(rightOpenAnchor, windowSize, LayoutDirection.Rtl, childSize)
        assertEquals(parentLeft + parentWidth, rightPos.x)
        assertEquals(parentTop, rightPos.y)

        val flipParentLeft = windowSize.width - parentWidth - 20
        val leftFlipAnchor =
            IntRect(
                left = flipParentLeft + horizontalInset,
                top = parentTop + verticalInset,
                right = flipParentLeft + parentWidth - horizontalInset,
                bottom = parentTop + verticalInset + 28,
            )
        val leftPos = provider.calculatePosition(leftFlipAnchor, windowSize, LayoutDirection.Rtl, childSize)
        assertEquals(flipParentLeft - childSize.width, leftPos.x)
        assertEquals(parentTop, leftPos.y)
    }
}
