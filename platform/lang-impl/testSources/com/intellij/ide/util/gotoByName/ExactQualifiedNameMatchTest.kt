// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider.isExactQualifiedMatch
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider.isInExactMatchDegreeRange
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider.normalizeExactMatchPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards IJPL-220122: a class or a symbol whose qualified name is exactly the search text must
 * outrank a partial match. [DefaultChooseByNameItemProvider] adds
 * [DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE] to the weight of such an element.
 */
class ExactQualifiedNameMatchTest {
  @Test
  fun `a pattern equal to the whole qualified name is an exact match`() {
    assertTrue(isExactQualifiedMatch("foo${SEP}Bar${SEP}baz", "foo${SEP}Bar${SEP}baz"))
  }

  @Test
  fun `a pattern that matches a suffix at a separator is an exact match`() {
    assertTrue(isExactQualifiedMatch("Bar${SEP}baz", "com${SEP}foo${SEP}Bar${SEP}baz"))
    assertTrue(isExactQualifiedMatch("baz", "com${SEP}foo${SEP}Bar${SEP}baz"))
  }

  @Test
  fun `a decorated container text is an exact match`() {
    // GotoSymbolModel2.getFullName returns "of com.foo.Bar.baz" for a Java member. The prefix comes
    // from the JavaPsiBundle message "aux.context.display", so a translation can change it.
    // The check therefore accepts any character that cannot continue an identifier.
    assertTrue(isExactQualifiedMatch("com${SEP}foo${SEP}Bar${SEP}baz", "of com${SEP}foo${SEP}Bar${SEP}baz"))
    assertTrue(isExactQualifiedMatch("Bar${SEP}baz", "of com${SEP}foo${SEP}Bar${SEP}baz"))
  }

  @Test
  fun `a longer name is not an exact match`() {
    assertFalse(isExactQualifiedMatch("Bar${SEP}baz", "of foo${SEP}MyBar${SEP}baz"))
    assertFalse(isExactQualifiedMatch("baz", "of foo${SEP}Bar${SEP}otherBaz"))
  }

  @Test
  fun `a pattern longer than the qualified name is not an exact match`() {
    assertFalse(isExactQualifiedMatch("com${SEP}foo${SEP}Bar${SEP}baz", "Bar${SEP}baz"))
  }

  @Test
  fun `the comparison is case-sensitive`() {
    assertFalse(isExactQualifiedMatch("foo${SEP}bar${SEP}baz", "of foo${SEP}Bar${SEP}baz"))
  }

  @Test
  fun `every separator becomes the universal separator`() {
    val expected = "foo${SEP}Bar${SEP}baz"
    assertEquals(expected, normalizeExactMatchPattern(SYMBOL_SEPARATORS, "foo.Bar#baz"))
    assertEquals(expected, normalizeExactMatchPattern(SYMBOL_SEPARATORS, "foo.Bar.baz"))
    assertEquals(expected, normalizeExactMatchPattern(SYMBOL_SEPARATORS, $$"foo$Bar$baz"))
  }

  @Test
  fun `a pattern the search text cannot express is null`() {
    // A bare short name says nothing about the qualifier.
    assertNull(normalizeExactMatchPattern(SYMBOL_SEPARATORS, "Foo"))
    // A wildcard is not a literal name.
    assertNull(normalizeExactMatchPattern(SYMBOL_SEPARATORS, "foo.*"))
    assertNull(normalizeExactMatchPattern(SYMBOL_SEPARATORS, "foo*Bar"))
    assertNull(normalizeExactMatchPattern(SYMBOL_SEPARATORS, ""))
    assertNull(normalizeExactMatchPattern(SYMBOL_SEPARATORS, "   "))
  }

  @Test
  fun `a trailing space is removed`() {
    assertEquals("foo${SEP}Bar", normalizeExactMatchPattern(SYMBOL_SEPARATORS, "foo.Bar "))
  }

  @Test
  fun `a weight with the bonus is in the exact match degree range`() {
    assertTrue(isInExactMatchDegreeRange(EXACT_MATCH_DEGREE))
    // A gap penalty can reduce the sum.
    assertTrue(isInExactMatchDegreeRange(EXACT_MATCH_DEGREE - 1000))
    // A class and a symbol add the bonus to a name degree that already holds the start match weight.
    assertTrue(isInExactMatchDegreeRange(EXACT_MATCH_DEGREE + START_MATCH_WEIGHT + 1100))
  }

  @Test
  fun `a weight without the bonus is not in the exact match degree range`() {
    assertFalse(isInExactMatchDegreeRange(0))
    assertFalse(isInExactMatchDegreeRange(EXACT_MATCH_DEGREE - 1001))
    // The largest realistic degree of a name that starts with the search text.
    assertFalse(isInExactMatchDegreeRange(START_MATCH_WEIGHT + 1100))
  }
}

/** The bonus that `PreferStartMatchMatcherWrapper` adds when a name starts with the search text. */
private const val START_MATCH_WEIGHT = 10000

/** The separators of the Symbols tab. See `GotoClassModel2.getSeparatorsFromContributors`. */
private val SYMBOL_SEPARATORS = arrayOf(".", "$", "#")

private const val SEP = "\u0000"
