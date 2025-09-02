// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search.impl

import org.jetbrains.jewel.foundation.matcher.assertIs
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

public class SubstringSpeedSearchMatcherTest {
    @Test
    public fun `should return null for blank pattern`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher(" ")
        assertIs<SpeedSearchMatcher.MatchResult.NoMatch>(matcher.matches("Some text"))
    }

    @Test
    public fun `should return null for null text`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("pattern")
        assertIs<SpeedSearchMatcher.MatchResult.NoMatch>(matcher.matches(null))
    }

    @Test
    public fun `should return null for blank text`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("pattern")
        assertIs<SpeedSearchMatcher.MatchResult.NoMatch>(matcher.matches(""))
        assertIs<SpeedSearchMatcher.MatchResult.NoMatch>(matcher.matches(" "))
    }

    @Test
    public fun `should return null when pattern not found`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("xyz")
        assertIs<SpeedSearchMatcher.MatchResult.NoMatch>(matcher.matches("abcdef"))
    }

    @Test
    public fun `should find pattern and return correct range`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("cd")
        val result = matcher.matches("abcdef")

        assertIs<SpeedSearchMatcher.MatchResult.Match>(result)
        assertEquals(2 until 4, result.ranges[0])
    }

    @Test
    public fun `should only return the first occurrence of the pattern`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("eye")
        val result = matcher.matches("an eye for an eye")

        assertIs<SpeedSearchMatcher.MatchResult.Match>(result)
        assertEquals(3 until 6, result.ranges[0])
    }

    @Test
    public fun `should find pattern at beginning of text`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("ab")
        val result = matcher.matches("abcdef")

        assertIs<SpeedSearchMatcher.MatchResult.Match>(result)
        assertEquals(0 until 2, result.ranges[0])
    }

    @Test
    public fun `should find pattern at end of text`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("ef")
        val result = matcher.matches("abcdef")

        assertIs<SpeedSearchMatcher.MatchResult.Match>(result)
        assertEquals(4 until 6, result.ranges[0])
    }

    @Test
    public fun `should find pattern with ignoreCase by default`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("CD", ignoreCase = true)
        val result = matcher.matches("abcdef")

        assertIs<SpeedSearchMatcher.MatchResult.Match>(result)
        assertEquals(2 until 4, result.ranges[0])
    }

    @Test
    public fun `should not find pattern with case sensitivity enabled`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("CD", ignoreCase = false)
        assertIs<SpeedSearchMatcher.MatchResult.NoMatch>(matcher.matches("abcdef"))
    }

    @Test
    public fun `should find pattern with case sensitivity enabled when case matches`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("CD", ignoreCase = false)
        val result = matcher.matches("abCDef")

        assertIs<SpeedSearchMatcher.MatchResult.Match>(result)
        assertEquals(2 until 4, result.ranges[0])
    }

    @Test
    public fun `should find pattern with special characters`() {
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("c-d")
        val result = matcher.matches("abc-def")

        assertIs<SpeedSearchMatcher.MatchResult.Match>(result)
        assertEquals(2 until 5, result.ranges[0])
    }
}
