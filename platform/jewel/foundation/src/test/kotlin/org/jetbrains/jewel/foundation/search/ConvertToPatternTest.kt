// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search

import org.junit.Assert.assertEquals
import org.junit.Test

public class ConvertToPatternTest {
    @Test
    public fun `one word is not split`() {
        assertEquals("*convert", "convert".convertToPattern(false))
        assertEquals("convert", "convert".convertToPattern(true))
    }

    @Test
    public fun `should split words and add wildcards`() {
        assertEquals("*convert*To*Pattern", "convertToPattern".convertToPattern(false))
        assertEquals("convert*To*Pattern", "convertToPattern".convertToPattern(true))
    }

    @Test
    public fun `multiple upper cases are grouped by two`() {
        assertEquals("*IO*SHttp*Request", "IOSHttpRequest".convertToPattern(false))
        assertEquals("IO*SHttp*Request", "IOSHttpRequest".convertToPattern(true))
    }

    @Test
    public fun `should split digits from letters`() {
        assertEquals("*Version*2*Alpha", "Version2Alpha".convertToPattern(false))
        assertEquals("Version*2*Alpha", "Version2Alpha".convertToPattern(true))
        assertEquals("*123", "123".convertToPattern(false))
        assertEquals("123", "123".convertToPattern(true))
    }

    @Test
    public fun `should split digits from separators`() {
        assertEquals("*-*foo*_*bar", "-foo_bar".convertToPattern(false))
        assertEquals("-*foo*_*bar", "-foo_bar".convertToPattern(true))
    }

    @Test
    public fun `should keep blank input unchanged`() {
        assertEquals("", "".convertToPattern(false))
        assertEquals(" ", " ".convertToPattern(false))
        assertEquals("", "".convertToPattern(true))
        assertEquals(" ", " ".convertToPattern(true))
    }
}
