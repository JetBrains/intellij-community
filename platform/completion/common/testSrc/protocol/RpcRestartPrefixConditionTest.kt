// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.patterns.StandardPatterns
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [RpcRestartPrefixCondition] serialization and deserialization.
 *
 * Tests use [toRpc] to convert patterns to RPC format, validating the full conversion pipeline
 * including extension point-based converter selection.
 */
@TestApplication
class RpcRestartPrefixConditionTest {

  // ============================================================================
  // Tests for legacy support
  // ============================================================================

  @Test
  fun `test legacy EqualsTo fromRpc support`() {
    // Test backward compatibility with legacy EqualsTo class
    val legacyCondition = RpcRestartPrefixCondition.EqualsTo("test")
    val pattern = legacyCondition.fromRpc()

    assertTrue(pattern.accepts("test"), "'test' should match EqualsTo('test')")
    assertFalse(pattern.accepts("other"), "'other' should not match EqualsTo('test')")
  }

  // ============================================================================
  // Tests for full pattern -> toRpc -> fromRpc roundtrip
  // ============================================================================

  @Test
  fun `test AlwaysTrue roundtrip via toRpc`() {
    val original = StandardPatterns.string()
    val rpc = original.toRpc()

    assertSerializedDescriptor<AlwaysTrueDescriptor>(rpc)

    val reconstructed = rpc.fromRpc()
    assertTrue(reconstructed.accepts("any string"))
    assertTrue(reconstructed.accepts(""))
  }

  @Test
  fun `test LongerThan roundtrip via toRpc`() {
    val original = StandardPatterns.string().longerThan(5)
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<LongerThanDescriptor>(rpc)
    assertEquals(5, descriptor.minLength)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "a", "ab", "abc", "abcd", "abcde", "abcdef")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test ShorterThan roundtrip via toRpc`() {
    val original = StandardPatterns.string().shorterThan(3)
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<ShorterThanDescriptor>(rpc)
    assertEquals(3, descriptor.maxLength)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "a", "ab", "abc", "abcd")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test WithLength roundtrip via toRpc`() {
    val original = StandardPatterns.string().withLength(5)
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<WithLengthDescriptor>(rpc)
    assertEquals(5, descriptor.length)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "a", "1234", "12345", "123456")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test StartsWith roundtrip via toRpc`() {
    val original = StandardPatterns.string().startsWith("foo")
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<StartsWithDescriptor>(rpc)
    assertEquals("foo", descriptor.prefix)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "foo", "foobar", "barfoo", "FOO")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test EndsWith roundtrip via toRpc`() {
    val original = StandardPatterns.string().endsWith("-")
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<EndsWithDescriptor>(rpc)
    assertEquals("-", descriptor.suffix)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "-", "foo-", "-foo", "foo")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Contains roundtrip via toRpc`() {
    val original = StandardPatterns.string().contains("bar")
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<ContainsDescriptor>(rpc)
    assertEquals("bar", descriptor.substring)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "bar", "foobar", "barbaz", "baz")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Matches roundtrip via toRpc`() {
    val original = StandardPatterns.string().matches("foo.*bar")
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<MatchesDescriptor>(rpc)
    assertEquals("foo.*bar", descriptor.regex)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "foobar", "foo123bar", "barfoo", "foobaz")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test OneOf roundtrip via toRpc`() {
    val original = StandardPatterns.string().oneOf("get", "set")
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<OneOfDescriptor>(rpc)
    assertEquals(listOf("get", "set"), descriptor.values)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "get", "set", "GET", "put", "getter")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test OneOfIgnoreCase roundtrip via toRpc`() {
    val original = StandardPatterns.string().oneOfIgnoreCase("GET", "SET")
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<OneOfIgnoreCaseDescriptor>(rpc)
    assertEquals(listOf("GET", "SET"), descriptor.values)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "get", "GET", "Get", "set", "SET", "Set", "put")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test EqualTo roundtrip via toRpc`() {
    val original = StandardPatterns.string().equalTo("exact")
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<EqualToDescriptor>(rpc)
    assertEquals("exact", descriptor.value)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "exact", "EXACT", "exacty", "exac")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test EndsWithUppercaseLetter roundtrip via toRpc`() {
    val original = StandardPatterns.string().endsWithUppercaseLetter()
    val rpc = original.toRpc()

    assertSerializedDescriptor<EndsWithUppercaseLetterDescriptor>(rpc)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "a", "A", "abc", "abC", "ABC", "ab1", "ab!", "getA", "getAbc")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test AfterNonJavaIdentifierPart roundtrip via toRpc`() {
    val original = StandardPatterns.string().afterNonJavaIdentifierPart()
    val rpc = original.toRpc()

    assertSerializedDescriptor<AfterNonJavaIdentifierPartDescriptor>(rpc)

    val reconstructed = rpc.fromRpc()
    // Pattern matches when: length > 1 AND second-to-last char is NOT a Java identifier part
    for (input in listOf(
      "",       // too short - false
      "a",      // too short - false
      "ab",     // 'a' is identifier part - false
      "a b",    // ' ' is not identifier part - true
      " b",     // ' ' is not identifier part - true
      "a!b",    // '!' is not identifier part - true
      "a.b",    // '.' is not identifier part - true
      "abc",    // 'b' is identifier part - false
      "a1b",    // '1' is identifier part - false
      "a_b",    // '_' is identifier part - false
    )) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Or roundtrip via toRpc`() {
    val original = StandardPatterns.or(
      StandardPatterns.string().endsWith("-"),
      StandardPatterns.string().endsWith("_")
    )
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<OrDescriptor>(rpc)
    assertEquals(2, descriptor.conditions.size)
    assertInstanceOf(EndsWithDescriptor::class.java, descriptor.conditions[0])
    assertInstanceOf(EndsWithDescriptor::class.java, descriptor.conditions[1])

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "foo-", "bar_", "baz", "-", "_")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Not roundtrip via toRpc`() {
    val original = StandardPatterns.not(StandardPatterns.string().startsWith("x"))
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<NotDescriptor>(rpc)
    assertInstanceOf(StartsWithDescriptor::class.java, descriptor.condition)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "foo", "xfoo", "x", "X")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  // ============================================================================
  // Tests for composite patterns with nested conversions
  // ============================================================================

  @Test
  fun `test nested Or with multiple conditions roundtrip via toRpc`() {
    val original = StandardPatterns.or(
      StandardPatterns.string().startsWith("get"),
      StandardPatterns.string().startsWith("set"),
      StandardPatterns.string().startsWith("is")
    )
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<OrDescriptor>(rpc)
    assertEquals(3, descriptor.conditions.size)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "getValue", "setValue", "isEnabled", "doSomething")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Not with LongerThan roundtrip via toRpc`() {
    val original = StandardPatterns.not(StandardPatterns.string().longerThan(5))
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<NotDescriptor>(rpc)
    assertInstanceOf(LongerThanDescriptor::class.java, descriptor.condition)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "a", "abcde", "abcdef", "abcdefg")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test And roundtrip via toRpc`() {
    val original = StandardPatterns.string().and(StandardPatterns.string().endsWith("-"))
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<AndDescriptor>(rpc)
    assertInstanceOf(AlwaysTrueDescriptor::class.java, descriptor.base)
    assertInstanceOf(EndsWithDescriptor::class.java, descriptor.combined)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "a", "a-", "-", "test-", "test")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test And with afterNonJavaIdentifierPart and Or roundtrip via toRpc`() {
    // This is the pattern used by endsWithOneOf()
    val original = StandardPatterns.string()
      .afterNonJavaIdentifierPart()
      .and(StandardPatterns.or(
        StandardPatterns.string().endsWith("abc"),
        StandardPatterns.string().endsWith("def")
      ))
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<AndDescriptor>(rpc)
    assertInstanceOf(AfterNonJavaIdentifierPartDescriptor::class.java, descriptor.base)
    assertInstanceOf(OrDescriptor::class.java, descriptor.combined)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "a", " abc", ".def", "xabc", "x.abc", " x")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test endsWithOneOf pattern roundtrip via toRpc`() {
    // endsWithOneOf internally uses: .and(StandardPatterns.or(endsWith patterns...))
    val original = StandardPatterns.string()
      .afterNonJavaIdentifierPart()
      .endsWithOneOf(listOf("key1", "key2"))
    val rpc = original.toRpc()

    val descriptor = assertSerializedDescriptor<AndDescriptor>(rpc)
    assertInstanceOf(AfterNonJavaIdentifierPartDescriptor::class.java, descriptor.base)
    assertInstanceOf(OrDescriptor::class.java, descriptor.combined)

    val reconstructed = rpc.fromRpc()
    for (input in listOf("", "key1", " key1", ".key2", "xkey1", "x.key1")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  // ============================================================================
  // Tests for converter rejection of incompatible patterns
  // ============================================================================

  @Test
  fun `test pattern with multiple incompatible conditions returns AlwaysTrue`() {
    // A pattern that no single converter can handle should fall back to AlwaysTrue
    // (This tests the fallback behavior when no converter matches)
    val pattern = StandardPatterns.string().longerThan(5).shorterThan(10)
    val rpc = pattern.toRpc()

    // Since no single converter handles this combination, it falls back to AlwaysTrue
    assertInstanceOf(RpcRestartPrefixCondition.AlwaysTrue::class.java, rpc)
  }

  // ============================================================================
  // Helper methods
  // ============================================================================

  private inline fun <reified T> assertSerializedDescriptor(rpc: RpcRestartPrefixCondition): T {
    assertInstanceOf(RpcRestartPrefixCondition.Serialized::class.java, rpc,
                     "Expected RpcRestartPrefixCondition.Serialized but got ${rpc::class.simpleName}")
    val serialized = rpc as RpcRestartPrefixCondition.Serialized
    val descriptor = serialized.descriptor
    assertNotNull(descriptor, "Descriptor should not be null")
    assertInstanceOf(T::class.java, descriptor,
                     "Expected ${T::class.simpleName} but got ${descriptor::class.simpleName}")
    @Suppress("UNCHECKED_CAST")
    return descriptor as T
  }
}
