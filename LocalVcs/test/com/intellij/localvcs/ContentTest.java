package com.intellij.localvcs;

import org.junit.Test;


public class ContentTest extends LocalVcsTestCase {
  @Test
  public void testEqualityAndHash() {
    assertTrue(new Content(null, 1).equals(new Content(null, 1)));

    assertFalse(new Content(null, 1).equals(null));
    assertFalse(new Content(null, 1).equals(new Content(null, 2)));

    assertTrue(new Content(null, 1).hashCode() == new Content(null, 1).hashCode());
    assertTrue(new Content(null, 1).hashCode() != new Content(null, 2).hashCode());
  }
}
