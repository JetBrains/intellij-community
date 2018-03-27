/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.jetbrains.jetCheck.Generator.*;

/**
 * @author peter
 */
public class ShrinkTest extends PropertyCheckerTestCase {
  public void testShrinkingComplexString() {
    checkFalsified(listsOf(stringsOf(asciiPrintableChars())),
                   l -> {
                     String s = l.toString();
                     return !"abcdefghijklmnopqrstuvwxyz()[]#!".chars().allMatch(c -> s.indexOf((char)c) >= 0);
                   },
                   258);
  }

  public void testShrinkingNonEmptyList() {
    List<Integer> list = checkGeneratesExample(nonEmptyLists(integers(0, 100)),
                                               l -> l.contains(42),
                                               12);
    assertEquals(1, list.size());
  }

  public void testWhenEarlyObjectsCannotBeShrunkBeforeLater() {
    Generator<String> gen = listsOf(IntDistribution.uniform(0, 2), listsOf(IntDistribution.uniform(0, 2), sampledFrom('a', 'b'))).map(List::toString);
    Set<String> failing = new HashSet<>(Arrays.asList("[[a, b], [a, b]]", "[[a, b], [a]]", "[[a], [a]]", "[[a]]", "[]"));
    Predicate<String> property = s -> !failing.contains(s);
    checkFalsified(gen, property, 0); // prove that it sometimes fails
    for (int i = 0; i < 1000; i++) {
      try {
        PropertyChecker.forAll(gen).silently().shouldHold(property);
      }
      catch (PropertyFalsified e) {
        assertEquals("[]", e.getBreakingValue());
      }
    }
  }

}
