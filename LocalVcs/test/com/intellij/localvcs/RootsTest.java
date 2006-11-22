package com.intellij.localvcs;

import org.junit.Test;

public class RootsTest extends TestCase {
  private Roots roots = new Roots();

  @Test
  public void testCreatingRoots() {
    roots.createRoot("c:/root1");
    roots.createRoot("c:/root2");

    roots.hasEntry("c:/root1");
    roots.hasEntry("c:/root2");
  }

  @Test
  public void testCreatingEntriesUnderRoot() {
    roots.createRoot("c:/root");
    roots.createFile(null, "c:/root/entry", null, null);

    assertTrue(roots.hasEntry("c:/root/entry"));
  }

  @Test
  public void testMovingEntriesBetweenRoots() {
    roots.createRoot("c:/root1");
    roots.createRoot("c:/root2");

    roots.createFile(null, "c:/root1/file", null, null);
    roots.move("c:/root1/file", "c:/root2", null);

    assertFalse(roots.hasEntry("c:/root1/file"));
    assertTrue(roots.hasEntry("c:/root2/file"));
  }

  @Test
  public void testCanNotCreateEntryNotUnderExistingRoots() {
    try {
      roots.createFile(null, "c:/non-existing-root/file", null, null);
      fail();
    }
    catch (LocalVcsException e) {
    }
  }

  @Test
  public void testDeletingRoots() {
    roots.createRoot("c:/root");
    assertTrue(roots.hasEntry("c:/root"));

    roots.deleteRoot("c:/root");
    assertFalse(roots.hasEntry("c:/root"));
  }
}
