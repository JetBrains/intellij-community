// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.patterns.StandardPatterns
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

/**
 * Tests for [RpcPrefixCondition] serialization and deserialization.
 *
 * Tests use converters directly to create descriptors from patterns, avoiding the need for
 * extension points while still testing the full conversion pipeline.
 */
class RpcPrefixConditionTest {

  // ============================================================================
  // Tests for legacy support
  // ============================================================================

  @Test
  fun `test legacy EqualsTo fromRpc support`() {
    // Test backward compatibility with legacy EqualsTo class
    val legacyCondition = RpcPrefixCondition.EqualsTo("test")
    val pattern = legacyCondition.fromRpc()

    assertTrue(pattern.accepts("test"), "'test' should match EqualsTo('test')")
    assertFalse(pattern.accepts("other"), "'other' should not match EqualsTo('test')")
  }

  // ============================================================================
  // Tests for full pattern → converter → descriptor → fromRpc roundtrip
  // ============================================================================

  @Test
  fun `test AlwaysTrue roundtrip via converter`() {
    val original = StandardPatterns.string()
    val converter = AlwaysTrueConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(AlwaysTrueDescriptor::class.java, descriptor)

    val rpc = RpcPrefixCondition.Serialized(descriptor!!)
    val reconstructed = rpc.fromRpc()

    assertTrue(reconstructed.accepts("any string"))
    assertTrue(reconstructed.accepts(""))
  }

  @Test
  fun `test LongerThan roundtrip via converter`() {
    val original = StandardPatterns.string().longerThan(5)
    val converter = LongerThanConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(LongerThanDescriptor::class.java, descriptor)
    assertEquals(5, (descriptor as LongerThanDescriptor).minLength)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "a", "ab", "abc", "abcd", "abcde", "abcdef")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test ShorterThan roundtrip via converter`() {
    val original = StandardPatterns.string().shorterThan(3)
    val converter = ShorterThanConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(ShorterThanDescriptor::class.java, descriptor)
    assertEquals(3, (descriptor as ShorterThanDescriptor).maxLength)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "a", "ab", "abc", "abcd")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test WithLength roundtrip via converter`() {
    val original = StandardPatterns.string().withLength(5)
    val converter = WithLengthConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(WithLengthDescriptor::class.java, descriptor)
    assertEquals(5, (descriptor as WithLengthDescriptor).length)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "a", "1234", "12345", "123456")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test StartsWith roundtrip via converter`() {
    val original = StandardPatterns.string().startsWith("foo")
    val converter = StartsWithConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(StartsWithDescriptor::class.java, descriptor)
    assertEquals("foo", (descriptor as StartsWithDescriptor).prefix)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "foo", "foobar", "barfoo", "FOO")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test EndsWith roundtrip via converter`() {
    val original = StandardPatterns.string().endsWith("-")
    val converter = EndsWithConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(EndsWithDescriptor::class.java, descriptor)
    assertEquals("-", (descriptor as EndsWithDescriptor).suffix)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "-", "foo-", "-foo", "foo")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Contains roundtrip via converter`() {
    val original = StandardPatterns.string().contains("bar")
    val converter = ContainsConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(ContainsDescriptor::class.java, descriptor)
    assertEquals("bar", (descriptor as ContainsDescriptor).substring)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "bar", "foobar", "barbaz", "baz")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Matches roundtrip via converter`() {
    val original = StandardPatterns.string().matches("foo.*bar")
    val converter = MatchesConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(MatchesDescriptor::class.java, descriptor)
    assertEquals("foo.*bar", (descriptor as MatchesDescriptor).regex)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "foobar", "foo123bar", "barfoo", "foobaz")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test OneOf roundtrip via converter`() {
    val original = StandardPatterns.string().oneOf("get", "set")
    val converter = OneOfConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(OneOfDescriptor::class.java, descriptor)
    assertEquals(listOf("get", "set"), (descriptor as OneOfDescriptor).values)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "get", "set", "GET", "put", "getter")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test OneOfIgnoreCase roundtrip via converter`() {
    val original = StandardPatterns.string().oneOfIgnoreCase("GET", "SET")
    val converter = OneOfIgnoreCaseConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(OneOfIgnoreCaseDescriptor::class.java, descriptor)
    assertEquals(listOf("GET", "SET"), (descriptor as OneOfIgnoreCaseDescriptor).values)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "get", "GET", "Get", "set", "SET", "Set", "put")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test EqualTo roundtrip via converter`() {
    val original = StandardPatterns.string().equalTo("exact")
    val converter = EqualToConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(EqualToDescriptor::class.java, descriptor)
    assertEquals("exact", (descriptor as EqualToDescriptor).value)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "exact", "EXACT", "exacty", "exac")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test EndsWithUppercaseLetter roundtrip via converter`() {
    val original = StandardPatterns.string().endsWithUppercaseLetter()
    val converter = EndsWithUppercaseLetterConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(EndsWithUppercaseLetterDescriptor::class.java, descriptor)

    val rpc = RpcPrefixCondition.Serialized(descriptor!!)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "a", "A", "abc", "abC", "ABC", "ab1", "ab!", "getA", "getAbc")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test AfterNonJavaIdentifierPart roundtrip via converter`() {
    val original = StandardPatterns.string().afterNonJavaIdentifierPart()
    val converter = AfterNonJavaIdentifierPartConditionConverter()
    val descriptor = converter.toDescriptor(original)

    assertNotNull(descriptor)
    assertInstanceOf(AfterNonJavaIdentifierPartDescriptor::class.java, descriptor)

    val rpc = RpcPrefixCondition.Serialized(descriptor!!)
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
  fun `test Or descriptor roundtrip`() {
    // OrPatternConverter uses extension points for inner patterns, so we test the descriptor directly
    val descriptor = OrDescriptor(listOf(
      EndsWithDescriptor("-"),
      EndsWithDescriptor("_")
    ))

    val original = StandardPatterns.or(
      StandardPatterns.string().endsWith("-"),
      StandardPatterns.string().endsWith("_")
    )

    assertEquals(2, descriptor.conditions.size)
    assertInstanceOf(EndsWithDescriptor::class.java, descriptor.conditions[0])
    assertInstanceOf(EndsWithDescriptor::class.java, descriptor.conditions[1])

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "foo-", "bar_", "baz", "-", "_")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Not descriptor roundtrip`() {
    // NotPatternConverter uses extension points for inner pattern, so we test the descriptor directly
    val descriptor = NotDescriptor(StartsWithDescriptor("x"))

    val original = StandardPatterns.not(StandardPatterns.string().startsWith("x"))

    assertInstanceOf(StartsWithDescriptor::class.java, descriptor.condition)

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "foo", "xfoo", "x", "X")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  // ============================================================================
  // Tests for composite patterns with nested conversions
  // ============================================================================

  @Test
  fun `test nested Or with multiple conditions roundtrip`() {
    // For nested patterns, we need to manually build the descriptor since converters
    // use the extension point for inner patterns
    val descriptor = OrDescriptor(listOf(
      StartsWithDescriptor("get"),
      StartsWithDescriptor("set"),
      StartsWithDescriptor("is")
    ))

    val original = StandardPatterns.or(
      StandardPatterns.string().startsWith("get"),
      StandardPatterns.string().startsWith("set"),
      StandardPatterns.string().startsWith("is")
    )

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "getValue", "setValue", "isEnabled", "doSomething")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test Not with LongerThan roundtrip`() {
    val descriptor = NotDescriptor(LongerThanDescriptor(5))

    val original = StandardPatterns.not(StandardPatterns.string().longerThan(5))

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "a", "abcde", "abcdef", "abcdefg")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test And descriptor roundtrip`() {
    val descriptor = AndDescriptor(AlwaysTrueDescriptor, EndsWithDescriptor("-"))
    val original = StandardPatterns.string().and(StandardPatterns.string().endsWith("-"))

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "a", "a-", "-", "test-", "test")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test And with afterNonJavaIdentifierPart and Or roundtrip`() {
    // This is the pattern used by endsWithOneOf()
    val descriptor = AndDescriptor(
      AfterNonJavaIdentifierPartDescriptor,
      OrDescriptor(listOf(EndsWithDescriptor("abc"), EndsWithDescriptor("def")))
    )

    val original = StandardPatterns.string()
      .afterNonJavaIdentifierPart()
      .and(StandardPatterns.or(
        StandardPatterns.string().endsWith("abc"),
        StandardPatterns.string().endsWith("def")
      ))

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "a", " abc", ".def", "xabc", "x.abc", " x")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  @Test
  fun `test endsWithOneOf pattern roundtrip`() {
    // endsWithOneOf internally uses: .and(StandardPatterns.or(endsWith patterns...))
    // Build descriptor manually (converter uses extension points which need app context)
    val descriptor = AndDescriptor(
      AfterNonJavaIdentifierPartDescriptor,
      OrDescriptor(listOf(EndsWithDescriptor("key1"), EndsWithDescriptor("key2")))
    )

    val original = StandardPatterns.string()
      .afterNonJavaIdentifierPart()
      .endsWithOneOf(listOf("key1", "key2"))

    val rpc = RpcPrefixCondition.Serialized(descriptor)
    val reconstructed = rpc.fromRpc()

    for (input in listOf("", "key1", " key1", ".key2", "xkey1", "x.key1")) {
      assertEquals(original.accepts(input), reconstructed.accepts(input), "Mismatch for '$input'")
    }
  }

  // ============================================================================
  // Tests for converter rejection of incompatible patterns
  // ============================================================================

  @Test
  fun `test LongerThan converter rejects pattern with multiple conditions`() {
    val pattern = StandardPatterns.string().longerThan(5).shorterThan(10)
    val converter = LongerThanConditionConverter()

    assertNull(converter.toDescriptor(pattern), "Converter should reject patterns with multiple conditions")
  }

  @Test
  fun `test StartsWith converter rejects EndsWith pattern`() {
    val pattern = StandardPatterns.string().endsWith("foo")
    val converter = StartsWithConditionConverter()

    assertNull(converter.toDescriptor(pattern), "Converter should reject incompatible patterns")
  }

  @Test
  fun `test AlwaysTrue converter rejects pattern with conditions`() {
    val pattern = StandardPatterns.string().longerThan(3)
    val converter = AlwaysTrueConditionConverter()

    assertNull(converter.toDescriptor(pattern), "Converter should reject patterns with conditions")
  }
}
