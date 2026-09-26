// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class CachedSpeedSearchMatcherTest {
    private fun countingMatcher(invocations: IntArray): SpeedSearchMatcher = SpeedSearchMatcher { text ->
        invocations[0]++
        if (text?.contains("foo") == true) {
            SpeedSearchMatcher.MatchResult.Match(listOf(0..2))
        } else {
            SpeedSearchMatcher.MatchResult.NoMatch
        }
    }

    @Test
    public fun `should not return a stale result after the text is mutated`() {
        val matcher = SpeedSearchMatcher.patternMatcher("foo").cached()
        val text = StringBuilder("foobar")

        val before = matcher.matches(text)
        assertTrue("expected a match for 'foobar', got $before", before is SpeedSearchMatcher.MatchResult.Match)

        text.setLength(0)
        text.append("zzz")

        val after = matcher.matches(text)
        assertTrue("expected no match for 'zzz', got $after", after is SpeedSearchMatcher.MatchResult.NoMatch)
    }

    @Test
    public fun `should reuse the cached result for equal text from different instances`() {
        val invocations = intArrayOf(0)
        val matcher = countingMatcher(invocations).cached()

        matcher.matches(StringBuilder("foobar"))
        matcher.matches(StringBuilder("foobar"))

        assertEquals("the delegate should only be consulted once for equal text", 1, invocations[0])
    }

    @Test
    public fun `should reuse the cached result for equal strings`() {
        val invocations = intArrayOf(0)
        val matcher = countingMatcher(invocations).cached()

        matcher.matches("foobar")
        matcher.matches("foobar")

        assertEquals(1, invocations[0])
    }

    @Test
    public fun `should not cache null or blank text`() {
        val invocations = intArrayOf(0)
        val matcher = countingMatcher(invocations).cached()

        matcher.matches(null)
        matcher.matches(null)
        matcher.matches("  ")
        matcher.matches("  ")

        assertEquals("blank and null text should always be delegated", 4, invocations[0])
    }
}
