package com.intellij.localvcs;

import org.junit.Test;

public class FileEntryTest extends TestCase {
  @Test
  public void testEquality() {
    FileEntry e = new FileEntry(1, "name", "content");

    assertTrue(e.equals(new FileEntry(1, "name", "content")));

    assertFalse(e.equals(new FileEntry(2, "name", "content")));
    assertFalse(e.equals(new FileEntry(1, "another name", "content")));
    assertFalse(e.equals(new FileEntry(1, "name", "another content")));

    assertFalse(e.equals(null));
    assertFalse(e.equals(new Object()));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testHashCodeThrowsException() {
    new FileEntry(1, "name", "content").hashCode();
  }
}
