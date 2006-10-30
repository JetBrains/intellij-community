package com.intellij.localvcs;

import org.junit.Test;

public class DirectoryRevisionTest extends TestCase {
  @Test
  public void testAddingChildren() {
    Revision dir = new DirectoryRevision(null, null);
    Revision file = new FileRevision(null, null, null);

    dir.addChild(file);

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));

    assertSame(dir, file.getParent());
  }

  @Test
  public void testRemovingChildren() {
    Revision dir = new DirectoryRevision(null, null);
    Revision file = new FileRevision(null, null, null);

    dir.addChild(file);
    assertFalse(dir.getChildren().isEmpty());

    dir.removeChild(file);
    assertTrue(dir.getChildren().isEmpty());
    assertNull(file.getParent());
  }

  @Test
  public void testPath() {
    Revision dir = new DirectoryRevision(null, "dir");
    Revision file = new FileRevision(null, "file", null);

    dir.addChild(file);

    assertEquals(fn("dir/file"), file.getPath());
  }

  @Test
  public void testPathWithoutParent() {
    assertEquals(fn("dir"), new DirectoryRevision(null, "dir").getPath());
    assertEquals(fn("file"), new FileRevision(null, "file", null).getPath());
  }

  @Test
  public void testFindingRevisionInTree() {
    Revision root = new DirectoryRevision(null, "root");
    Revision dir = new DirectoryRevision(null, "dir");
    Revision file1 = new FileRevision(null, "file1", null);
    Revision file2 = new FileRevision(null, "file2", null);

    root.addChild(dir);
    root.addChild(file1);
    dir.addChild(file2);

    assertSame(root, root.getRevision(fn("root")));
    assertSame(dir, root.getRevision(fn("root/dir")));
    assertSame(file1, root.getRevision(fn("root/file1")));
    assertSame(file2, root.getRevision(fn("root/dir/file2")));
  }

  @Test
  public void testDoesNotFindUnknownRevision() {
    Revision root = new DirectoryRevision(null, "root");
    Revision dir = new DirectoryRevision(null, "dir");
    Revision file = new FileRevision(null, "file", null);

    root.addChild(dir);
    dir.addChild(file);

    assertNull(root.getRevision(fn("unknown root")));
    assertNull(root.getRevision(fn("root/unknown dir")));
    assertNull(root.getRevision(fn("root/dir/unknown file")));
  }
}
