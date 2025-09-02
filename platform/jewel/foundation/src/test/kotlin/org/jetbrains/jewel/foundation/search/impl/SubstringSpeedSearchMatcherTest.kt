// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search.impl

import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class SubstringSpeedSearchMatcherTest {
    @Test
    public fun `should return null for blank pattern`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher(" ")
        assertNull(matcher.matches("Some text"))
    }

    @Test
    public fun `should return null for null text`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("pattern")
        assertNull(matcher.matches(null))
    }

    @Test
    public fun `should return null for blank text`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("pattern")
        assertNull(matcher.matches(""))
        assertNull(matcher.matches(" "))
    }

    @Test
    public fun `should return null when pattern not found`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("xyz")
        assertNull(matcher.matches("abcdef"))
    }

    @Test
    public fun `should find pattern and return correct range`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("cd")
        val result = matcher.matches("abcdef")

        assertEquals(1, result?.size)
        assertEquals(2..4, result?.get(0))
    }

    @Test
    public fun `should only return the first occurrence of the pattern`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("eye")
        val result = matcher.matches("an eye for an eye")

        assertEquals(1, result?.size)
        assertEquals(3..6, result?.get(0))
    }

    @Test
    public fun `should find pattern at beginning of text`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("ab")
        val result = matcher.matches("abcdef")

        assertEquals(1, result?.size)
        assertEquals(0..2, result?.get(0))
    }

    @Test
    public fun `should find pattern at end of text`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("ef")
        val result = matcher.matches("abcdef")

        assertEquals(1, result?.size)
        assertEquals(4..6, result?.get(0))
    }

    @Test
    public fun `should find pattern with ignoreCase by default`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("CD", ignoreCase = true)
        val result = matcher.matches("abcdef")

        assertEquals(1, result?.size)
        assertEquals(2..4, result?.get(0))
    }

    @Test
    public fun `should not find pattern with case sensitivity enabled`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("CD", ignoreCase = false)
        assertNull(matcher.matches("abcdef"))
    }

    @Test
    public fun `should find pattern with case sensitivity enabled when case matches`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("CD", ignoreCase = false)
        val result = matcher.matches("abCDef")

        assertEquals(1, result?.size)
        assertEquals(2..4, result?.get(0))
    }

    @Test
    public fun `should find pattern with special characters`() {
        val matcher = SpeedSearchMatcher.Companion.exactSubstringMatcher("c-d")
        val result = matcher.matches("abc-def")

        assertEquals(1, result?.size)
        assertEquals(2..5, result?.get(0))
    }
}
