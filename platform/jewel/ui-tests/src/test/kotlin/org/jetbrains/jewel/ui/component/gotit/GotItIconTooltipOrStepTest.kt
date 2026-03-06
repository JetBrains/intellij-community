// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import org.junit.Assert.assertEquals
import org.junit.Test

class GotItIconTooltipOrStepTest {
    @Test(expected = IllegalArgumentException::class)
    fun `Step with 0 throws`() {
        GotItIconOrStep.Step(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Step with 100 throws`() {
        GotItIconOrStep.Step(100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Step with negative number throws`() {
        GotItIconOrStep.Step(-1)
    }

    @Test
    fun `Step with 1 is valid`() {
        val step = GotItIconOrStep.Step(1)
        assertEquals(1, step.number)
    }

    @Test
    fun `Step with 99 is valid`() {
        val step = GotItIconOrStep.Step(99)
        assertEquals(99, step.number)
    }

    @Test
    fun `Step with 50 is valid`() {
        val step = GotItIconOrStep.Step(50)
        assertEquals(50, step.number)
    }

    @Test
    fun `Step 1 formats as 01`() {
        assertEquals("01", GotItIconOrStep.Step(1).formattedText)
    }

    @Test
    fun `Step 9 formats as 09`() {
        assertEquals("09", GotItIconOrStep.Step(9).formattedText)
    }

    @Test
    fun `Step 10 formats as 10`() {
        assertEquals("10", GotItIconOrStep.Step(10).formattedText)
    }

    @Test
    fun `Step 99 formats as 99`() {
        assertEquals("99", GotItIconOrStep.Step(99).formattedText)
    }
}
