package com.intellij.localvcs;

import org.junit.Test;

import java.util.List;

public class RootEntryRootsTest extends TestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testCreatingRoots() {
    root.createDirectory(null, "c:/dir/root", null);

    assertTrue(root.hasEntry("c:/dir/root"));
    assertFalse(root.hasEntry("c:/dir"));
  }

  @Test
  public void testCreatingEntriesUnderRoot() {
    root.createDirectory(null, "c:/root", null);
    root.createFile(null, "c:/root/entry", null, null);

    assertTrue(root.hasEntry("c:/root/entry"));
  }

  @Test
  public void testMovingEntriesBetweenRoots() {
    root.createDirectory(null, "c:/root1", null);
    root.createDirectory(null, "c:/root2", null);

    root.createFile(null, "c:/root1/file", null, null);
    root.move("c:/root1/file", "c:/root2");

    assertFalse(root.hasEntry("c:/root1/file"));
    assertTrue(root.hasEntry("c:/root2/file"));
  }

  @Test
  public void testCanNotCreateEntryNotUnderExistingRoots() {
    try {
      root.createFile(null, "c:/non-existing-root/file", null, null);
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRenamingRoots() {
    root.createDirectory(null, "c:/dir/root", null);
    root.rename("c:/dir/root", "newName");
    
    assertTrue(root.hasEntry("c:/dir/newName"));
    assertFalse(root.hasEntry("c:/dir/root"));
  }

  @Test
  public void testDeletingRoots() {
    root.createDirectory(null, "c:/dir/root", null);

    root.delete("c:/dir/root");
    assertFalse(root.hasEntry("c:/dir/root"));
  }

  @Test
  public void testGettingRoots() {
    root.createDirectory(null, "c:/root1", null);
    root.createDirectory(null, "c:/root2", null);
    root.createDirectory(null, "c:/root2/dir", null);

    List<Entry> roots = root.getRoots();
    assertEquals(2, roots.size());

    assertEquals("c:/root1", roots.get(0).getName());
    assertEquals("c:/root2", roots.get(1).getName());
  }
}
