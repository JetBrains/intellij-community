package com.intellij.localvcs;

import org.junit.Assert;
import org.junit.Test;

public class RevisionTest extends Assert {
  @Test
  public void testEquality() {
    Revision r = new Revision(1, "name", "content");

    assertTrue(r.equals(new Revision(1, "name", "content")));

    assertFalse(r.equals(new Revision(2, "name", "content")));
    assertFalse(r.equals(new Revision(1, "another name", "content")));
    assertFalse(r.equals(new Revision(1, "name", "another content")));

    assertFalse(r.equals(null));
    assertFalse(r.equals(new Object()));
  }
}
