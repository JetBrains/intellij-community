package com.intellij.localvcs;

import org.junit.Test;

public class DirectoryRevisionTest extends TestCase {
  @Test
  public void testAddingChildren() {
    Revision r = new DirectoryRevision(1, fn("dir"));
    fail();
  }
}
