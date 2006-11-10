package com.intellij.localvcs;

import org.junit.Test;

public class IdPathTest extends TestCase {
  @Test
  public void testAppending() {
    IdPath p = new IdPath(1, 2);
    assertEquals(new IdPath(1, 2, 3), p.appendedWith(3));
  }

  @Test
  public void testAppendingDoesNotChangeOriginal() {
    IdPath p = new IdPath(1, 2);
    p.appendedWith(3);
    assertEquals(new IdPath(1, 2), p);
  }

  @Test
  public void testContains() {
    IdPath p = new IdPath(1, 2);

    assertTrue(p.isPrefixOf(new IdPath(1, 2)));
    assertTrue(p.isPrefixOf(new IdPath(1, 2, 3)));

    assertFalse(p.isPrefixOf(new IdPath(1)));
    assertFalse(p.isPrefixOf(new IdPath(1, 3)));
  }
}
