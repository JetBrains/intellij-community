package com.intellij.localvcs;

import org.junit.Test;

import java.util.List;

public class RootEntryRootsTest extends LocalVcsTestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testCreatingRoots() {
    root.createDirectory(-1, "c:/dir/root");

    assertTrue(root.hasEntry("c:/dir/root"));
    assertFalse(root.hasEntry("c:/dir"));
  }

  @Test
  public void testCreatingEntriesUnderRoot() {
    root.createDirectory(-1, "c:/root");
    root.createFile(-1, "c:/root/entry", null, -1);

    assertTrue(root.hasEntry("c:/root/entry"));
  }

  @Test
  public void testMovingEntriesBetweenRoots() {
    root.createDirectory(-1, "c:/root1");
    root.createDirectory(-1, "c:/root2");

    root.createFile(-1, "c:/root1/file", null, -1);
    root.move("c:/root1/file", "c:/root2");

    assertFalse(root.hasEntry("c:/root1/file"));
    assertTrue(root.hasEntry("c:/root2/file"));
  }

  @Test
  public void testCanNotCreateEntryNotUnderExistingRoots() {
    try {
      root.createFile(-1, "c:/non-existing-root/file", null, -1);
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingRoots() {
    root.createDirectory(-1, "c:/dir/root");
    root.rename("c:/dir/root", "newName");

    assertTrue(root.hasEntry("c:/dir/newName"));
    assertFalse(root.hasEntry("c:/dir/root"));
  }

  @Test
  public void testDeletingRoots() {
    root.createDirectory(-1, "c:/dir/root");

    root.delete("c:/dir/root");
    assertFalse(root.hasEntry("c:/dir/root"));
  }

  @Test
  public void testGettingRoots() {
    root.createDirectory(-1, "c:/root1");
    root.createDirectory(-1, "c:/root2");
    root.createDirectory(-1, "c:/root2/dir");

    List<Entry> roots = root.getRoots();
    assertEquals(2, roots.size());

    assertEquals("c:/root1", roots.get(0).getName());
    assertEquals("c:/root2", roots.get(1).getName());
  }
}
