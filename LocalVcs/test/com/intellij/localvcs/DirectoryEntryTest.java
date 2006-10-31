package com.intellij.localvcs;

import java.util.List;

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

    assertSame(root, root.findEntry(p("root")));
    assertSame(dir, root.findEntry(p("root/dir")));
    assertSame(file1, root.findEntry(p("root/file1")));
    assertSame(file2, root.findEntry(p("root/dir/file2")));
  }

  @Test
  public void testDoesNotFindUnknownRevision() {
    Entry root = new DirectoryEntry(null, "root");
    Entry dir = new DirectoryEntry(null, "dir");
    Entry file = new FileEntry(null, "file", null);

    root.addChild(dir);
    dir.addChild(file);

    assertNull(root.findEntry(p("unknown root")));
    assertNull(root.findEntry(p("root/unknown dir")));
    assertNull(root.findEntry(p("root/dir/unknown file")));
  }

  @Test
  public void testCopyingWithContent() {
    Entry dir = new DirectoryEntry(42, "name");
    Entry copy = dir.copy();

    assertEquals(42, copy.getObjectId());
    assertEquals(p("name"), copy.getPath());
  }

  @Test
  public void testDoesNotCopyParent() {
    Entry parent = new DirectoryEntry(null, null);
    Entry dir = new DirectoryEntry(null, null);

    parent.addChild(dir);

    assertNull(dir.copy().getParent());
  }

  @Test
  public void testCopyingContentRecursively() {
    Entry dir = new DirectoryEntry(null, null);
    Entry child1 = new FileEntry(1, null, null);
    Entry child2 = new DirectoryEntry(2, null);
    Entry child3 = new FileEntry(3, null, null);

    dir.addChild(child1);
    dir.addChild(child2);
    child2.addChild(child3);

    Entry copy = dir.copy();
    List<Entry> children = copy.getChildren();

    assertEquals(2, children.size());
    assertEquals(1, children.get(1).getChildren().size());

    Entry copyChild1 = children.get(0);
    Entry copyChild2 = children.get(1);
    Entry copyChild3 = copyChild2.getChildren().get(0);

    assertEquals(1, copyChild1.getObjectId());
    assertEquals(2, copyChild2.getObjectId());
    assertEquals(3, copyChild3.getObjectId());

    assertSame(copy, copyChild1.getParent());
    assertSame(copy, copyChild2.getParent());
    assertSame(copyChild2, copyChild3.getParent());
  }

  @Test
  public void testCopyingContentDoesNotChangeOriginalStructure() {
    Entry dir = new DirectoryEntry(null, null);
    Entry child1 = new FileEntry(1, null, null);
    Entry child2 = new DirectoryEntry(2, null);
    Entry child3 = new FileEntry(3, null, null);

    dir.addChild(child1);
    dir.addChild(child2);
    child2.addChild(child3);

    dir.copy();

    assertSame(dir, child1.getParent());
    assertSame(dir, child2.getParent());
    assertSame(child2, child3.getParent());
  }

  @Test
  public void testRenaming() {
    DirectoryEntry root = new DirectoryEntry(null, "root");
    DirectoryEntry dir = new DirectoryEntry(33, "dir");
    FileEntry child = new FileEntry(44, "child", null);

    root.addChild(root);
    dir.addChild(child);

    Entry renamed = dir.renamed("new dir");

    assertNull(renamed.getParent());
    assertEquals(p("new dir"), renamed.getPath());

    List<Entry> newChildren = renamed.getChildren();
    assertEquals(1, renamed.getChildren().size());

    assertEquals(44, newChildren.get(0).getObjectId());

    assertSame(dir, child.getParent());
    assertSame(renamed, newChildren.get(0).getParent());
  }
}
