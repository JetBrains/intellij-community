// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

public class ParseHtmlSizeValueTest {
    @Test
    public fun `parses bare integer as pixels`() {
        assertEquals(DimensionSize.Pixels(100), "100".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `parses integer with semicolon as pixels`() {
        assertEquals(DimensionSize.Pixels(100), "100;".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `parses px as pixels`() {
        assertEquals(DimensionSize.Pixels(100), "100px".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `parses px with semicolon as pixels`() {
        assertEquals(DimensionSize.Pixels(100), "100px;".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `parses px uppercase`() {
        assertEquals(DimensionSize.Pixels(100), "100PX".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `parses px mixed case`() {
        assertEquals(DimensionSize.Pixels(100), "100Px".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `parses px with space between number and unit`() {
        assertEquals(DimensionSize.Pixels(100), "100 px".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `parses px uppercase with space and semicolon`() {
        assertEquals(DimensionSize.Pixels(100), "100 PX;".parseDimensionSize() as DimensionSize.Pixels)
    }

    @Test
    public fun `rejects percent`() {
        assertNull("50%".parseDimensionSize())
    }

    @Test
    public fun `rejects unknown unit`() {
        assertNull("100em".parseDimensionSize())
    }

    @Test
    public fun `rejects garbage after px`() {
        assertNull("50pxfoo".parseDimensionSize())
    }

    @Test
    public fun `rejects empty string`() {
        assertNull("".parseDimensionSize())
    }

    @Test
    public fun `rejects non-numeric input`() {
        assertNull("abc".parseDimensionSize())
    }
}
