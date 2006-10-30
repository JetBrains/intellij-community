package com.intellij.localvcs;

import org.junit.Test;

public class DirectoryEntryTest extends TestCase {
  @Test
  public void testAddingChildren() {
    Entry dir = new DirectoryEntry(null, null);
    Entry file = new FileEntry(null, null, null);

    dir.addChild(file);

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));

    assertSame(dir, file.getParent());
  }

  @Test
  public void testRemovingChildren() {
    Entry dir = new DirectoryEntry(null, null);
    Entry file = new FileEntry(null, null, null);

    dir.addChild(file);
    assertFalse(dir.getChildren().isEmpty());

    dir.removeChild(file);
    assertTrue(dir.getChildren().isEmpty());
    assertNull(file.getParent());
  }

  @Test
  public void testPath() {
    Entry dir = new DirectoryEntry(null, "dir");
    Entry file = new FileEntry(null, "file", null);

    dir.addChild(file);

    assertEquals(p("dir/file"), file.getPath());
  }

  @Test
  public void testPathWithoutParent() {
    assertEquals(p("dir"), new DirectoryEntry(null, "dir").getPath());
    assertEquals(p("file"), new FileEntry(null, "file", null).getPath());
  }

  @Test
  public void testFindingRevisionInTree() {
    Entry root = new DirectoryEntry(null, "root");
    Entry dir = new DirectoryEntry(null, "dir");
    Entry file1 = new FileEntry(null, "file1", null);
    Entry file2 = new FileEntry(null, "file2", null);

    root.addChild(dir);
    root.addChild(file1);
    dir.addChild(file2);

    assertSame(root, root.getEntry(p("root")));
    assertSame(dir, root.getEntry(p("root/dir")));
    assertSame(file1, root.getEntry(p("root/file1")));
    assertSame(file2, root.getEntry(p("root/dir/file2")));
  }

  @Test
  public void testDoesNotFindUnknownRevision() {
    Entry root = new DirectoryEntry(null, "root");
    Entry dir = new DirectoryEntry(null, "dir");
    Entry file = new FileEntry(null, "file", null);

    root.addChild(dir);
    dir.addChild(file);

    assertNull(root.getEntry(p("unknown root")));
    assertNull(root.getEntry(p("root/unknown dir")));
    assertNull(root.getEntry(p("root/dir/unknown file")));
  }
}
