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
    assertEquals(new IdPath(1), new IdPath(1, 2).getParent());
    assertNull(new IdPath(1).getParent());
  }

  @Test
  public void testContains() {
    IdPath p = new IdPath(1, 2);

    assertTrue(p.contains(1));
    assertTrue(p.contains(2));

    assertFalse(p.contains(3));
  }

  @Test
  public void testEquality() {
    assertTrue(new IdPath(1, 2, 3).equals(new IdPath(1, 2, 3)));
    assertFalse(new IdPath(1, 2, 3).equals(new IdPath(1, 2)));

    assertFalse(new IdPath(1, 2, 3).equals(null));
    assertFalse(new IdPath(1, 2, 3).equals(new Object()));
  }

  @Test
  public void testToString() {
    assertEquals("1.2.3", new IdPath(1, 2, 3).toString());
  }

  @Test
  public void testRootEquals() {
    assertTrue(new IdPath(1, 2, 3).rootEquals(1));
    assertTrue(new IdPath(1).rootEquals(1));

    assertFalse(new IdPath(1, 2, 3).rootEquals(2));
  }

  @Test
  public void testWithoutRoot() {
    assertEquals(new IdPath(2, 3), new IdPath(1, 2, 3).withoutRoot());
    assertEquals(new IdPath(2), new IdPath(1, 2).withoutRoot());
  }
}
