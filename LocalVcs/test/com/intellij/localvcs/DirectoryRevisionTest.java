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
    Revision dir = new DirectoryRevision(null, fn("dir"));
    Revision file = new FileRevision(null, fn("file"), "");

    dir.addChild(file);

    assertEquals(fn("dir/file"), file.getPath());
    assertEquals(fn("file"), file.getName());
  }

  @Test
  public void testPathWithoutParent() {
    assertEquals(fn("dir"), new DirectoryRevision(null, fn("dir")).getPath());
    assertEquals(fn("file"), new FileRevision(null, fn("file"), "").getPath());
  }

  @Test
  public void testFindingRevisionInTree() {
    Revision root = new DirectoryRevision(null, fn("root"));
    Revision dir = new DirectoryRevision(null, fn("dir"));
    Revision file1 = new FileRevision(null, fn("file1"), "");
    Revision file2 = new FileRevision(null, fn("file2"), "");

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
    Revision root = new DirectoryRevision(null, fn("root"));
    Revision dir = new DirectoryRevision(null, fn("dir"));
    Revision file = new FileRevision(null, fn("file"), "");

    root.addChild(dir);
    dir.addChild(file);

    assertNull(root.getRevision(fn("unknown root")));
    assertNull(root.getRevision(fn("root/unknown dir")));
    assertNull(root.getRevision(fn("root/dir/unknown file")));
  }
}
