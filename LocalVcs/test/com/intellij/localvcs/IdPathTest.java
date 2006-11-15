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
  public void testName() {
    assertEquals(3, new IdPath(1, 2, 3).getName());
  }

  @Test
  public void testParent() {
    assertEquals(new IdPath(1, 2), new IdPath(1, 2, 3).getParent());
    assertNull(new IdPath(1).getParent());
  }

  @Test
  public void testContains() {
    IdPath p = new IdPath(1, 2);

    assertTrue(p.contains(1));
    assertTrue(p.contains(2));

    assertFalse(p.contains(3));
  }
}
