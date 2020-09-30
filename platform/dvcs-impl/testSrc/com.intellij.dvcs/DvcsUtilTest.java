// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs;

import junit.framework.TestCase;

import java.util.Arrays;

public class DvcsUtilTest extends TestCase {
  public void testJoinWithAnd() {
    assertEquals("", DvcsUtil.joinWithAnd(Arrays.asList(""), 3));
    assertEquals("1", DvcsUtil.joinWithAnd(Arrays.asList("1"), 3));
    assertEquals("1 and 2", DvcsUtil.joinWithAnd(Arrays.asList("1", "2"), 3));
    assertEquals("1, 2 and 3", DvcsUtil.joinWithAnd(Arrays.asList("1", "2", "3"), 3));
    assertEquals("1, 2 and 2 others", DvcsUtil.joinWithAnd(Arrays.asList("1", "2", "3", "4"), 3));
  }
}
