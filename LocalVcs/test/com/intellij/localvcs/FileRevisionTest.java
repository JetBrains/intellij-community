package com.intellij.localvcs;

import org.junit.Assert;
import org.junit.Test;

public class FileRevisionTest extends Assert {
  @Test
  public void testEquality() {
    FileRevision r = new FileRevision(1, "name", "content");

    assertTrue(r.equals(new FileRevision(1, "name", "content")));

    assertFalse(r.equals(new FileRevision(2, "name", "content")));
    assertFalse(r.equals(new FileRevision(1, "another name", "content")));
    assertFalse(r.equals(new FileRevision(1, "name", "another content")));

    assertFalse(r.equals(null));
    assertFalse(r.equals(new Object()));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testHashCodeThrowsException() {
    new FileRevision(1, "name", "content").hashCode();
  }
}
