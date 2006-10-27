package com.intellij.localvcs;

import org.junit.Test;

public class RevisionTest extends TestCase {
  @Test
  public void testEquality() {
    FileRevision r = new FileRevision(1, fn("name"), "content");

    assertTrue(r.equals(new FileRevision(1, fn("name"), "content")));

    assertFalse(r.equals(new FileRevision(2, fn("name"), "content")));
    assertFalse(r.equals(new FileRevision(1, fn("another name"), "content")));
    assertFalse(r.equals(new FileRevision(1, fn("name"), "another content")));

    assertFalse(r.equals(null));
    assertFalse(r.equals(new Object()));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testHashCodeThrowsException() {
    new FileRevision(1, fn("name"), "content").hashCode();
  }
}
