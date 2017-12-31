/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package jetCheck;

import java.util.List;

import static jetCheck.Generator.*;

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
                   225);
  }

  public void testShrinkingNonEmptyList() {
    List<Integer> list = checkGeneratesExample(nonEmptyLists(integers(0, 100)),
                                               l -> l.contains(42),
                                               12);
    assertEquals(1, list.size());
  }

}
