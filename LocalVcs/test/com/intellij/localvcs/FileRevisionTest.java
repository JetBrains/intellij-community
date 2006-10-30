package com.intellij.localvcs;

import org.junit.Test;

public class FileRevisionTest extends TestCase {
  @Test
  public void testEquality() {
    FileEntry r = new FileEntry(1, "name", "content");

    assertTrue(r.equals(new FileEntry(1, "name", "content")));

    assertFalse(r.equals(new FileEntry(2, "name", "content")));
    assertFalse(r.equals(new FileEntry(1, "another name", "content")));
    assertFalse(r.equals(new FileEntry(1, "name", "another content")));

    assertFalse(r.equals(null));
    assertFalse(r.equals(new Object()));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testHashCodeThrowsException() {
    new FileEntry(1, "name", "content").hashCode();
  }
}
