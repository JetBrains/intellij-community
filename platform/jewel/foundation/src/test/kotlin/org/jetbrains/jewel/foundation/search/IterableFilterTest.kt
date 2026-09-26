// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search

import org.junit.Assert.assertEquals
import org.junit.Test

public class IterableFilterTest {
    // Test data classes
    private data class User(val name: String, val email: String)

    // Exact substring matcher tests
    @Test
    public fun `filter strings should return only exact matching items`() {
        val items = listOf("apple", "pineapple", "application", "banana")
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("apple")
        val result = items.filter(matcher)

        assertEquals(2, result.size)
        assertEquals(listOf("apple", "pineapple"), result)
    }

    @Test
    public fun `filter strings should return empty list when no items match`() {
        val items = listOf("apple", "banana", "cherry")
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("xyz")
        val result = items.filter(matcher)

        assertEquals(0, result.size)
    }

    @Test
    public fun `filter should work with custom types`() {
        val users =
            listOf(
                User("John Doe", "john@example.com"),
                User("Jane Smith", "jane@example.com"),
                User("Johnny Cash", "johnny@example.com"),
            )
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("jane")
        val result = users.filter(matcher) { it.name }

        assertEquals(1, result.size)
        assertEquals(listOf(users[1]), result)
    }

    @Test
    public fun `filter return empty list when no items match with custom types`() {
        val users =
            listOf(
                User("John Doe", "john@example.com"),
                User("Jane Smith", "jane@example.com"),
                User("Johnny Cash", "johnny@example.com"),
            )
        val matcher = SpeedSearchMatcher.exactSubstringMatcher("Joana")
        val result = users.filter(matcher) { it.name }

        assertEquals(0, result.size)
    }
}
