package com.intellij.localvcs;

import java.util.List;

import static com.intellij.localvcs.Difference.Kind.CREATED;
import static com.intellij.localvcs.Difference.Kind.DELETED;
import static com.intellij.localvcs.Difference.Kind.MODIFIED;
import static com.intellij.localvcs.Difference.Kind.NOT_MODIFIED;
import org.junit.Test;

public class DirectoryEntryTest extends TestCase {
  @Test
  public void testAddingChildren() {
    Entry dir = new DirectoryEntry(null, null, null);
    Entry file = new FileEntry(null, null, null, null);

    dir.addChild(file);

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));

    assertSame(dir, file.getParent());
  }

  @Test
  public void testAddingEntryWithExistingNameThrowsException() {
    Entry dir = new DirectoryEntry(null, null, null);
    dir.addChild(new FileEntry(null, "name", null, null));

    try {
      dir.addChild(new FileEntry(null, "name", null, null));
      fail();
    } catch (AssertionError e) {}
  }

  @Test
  public void testRemovingChildren() {
    Entry dir = new DirectoryEntry(null, null, null);
    Entry file = new FileEntry(null, null, null, null);

    dir.addChild(file);
    assertFalse(dir.getChildren().isEmpty());

    dir.removeChild(file);
    assertTrue(dir.getChildren().isEmpty());
    assertNull(file.getParent());
  }

  @Test
  public void testChildById() {
    Entry dir = new DirectoryEntry(null, null, null);
    Entry file1 = new FileEntry(1, "file1", null, null);
    Entry file2 = new FileEntry(2, "file2", null, null);

    dir.addChild(file1);
    dir.addChild(file2);

    assertSame(file1, dir.findChild(1));
    assertSame(file2, dir.findChild(2));

    assertNull(dir.findChild(99));
  }

  @Test
  public void testIdPath() {
    Entry dir = new DirectoryEntry(1, null, null);
    Entry file = new FileEntry(2, null, null, null);

    dir.addChild(file);

    assertEquals(idp(1, 2), file.getIdPath());
  }

  @Test
  public void testPath() {
    Entry dir = new DirectoryEntry(null, "dir", null);
    Entry file = new FileEntry(null, "file", null, null);

    dir.addChild(file);

    assertEquals("dir/file", file.getPath());
  }

  @Test
  public void testPathWithoutParent() {
    assertEquals("dir", new DirectoryEntry(null, "dir", null).getPath());
    assertEquals("file", new FileEntry(null, "file", null, null).getPath());
  }

  @Test
  public void testCopyingWithContent() {
    Entry dir = new DirectoryEntry(42, "name", null);
    Entry copy = dir.copy();

    assertEquals(42, copy.getId());
    assertEquals("name", copy.getPath());
  }

  @Test
  public void testDoesNotCopyParent() {
    Entry parent = new DirectoryEntry(null, null, null);
    Entry dir = new DirectoryEntry(null, null, null);

    parent.addChild(dir);

    assertNull(dir.copy().getParent());
  }

  @Test
  public void testCopyingContentRecursively() {
    Entry dir = new DirectoryEntry(null, null, null);
    Entry child1 = new FileEntry(1, "child1", null, null);
    Entry child2 = new DirectoryEntry(2, "child2", null);
    Entry child3 = new FileEntry(3, "child3", null, null);

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

    assertEquals(1, copyChild1.getId());
    assertEquals(2, copyChild2.getId());
    assertEquals(3, copyChild3.getId());

    assertSame(copy, copyChild1.getParent());
    assertSame(copy, copyChild2.getParent());
    assertSame(copyChild2, copyChild3.getParent());
  }

  @Test
  public void testCopyingContentDoesNotChangeOriginalStructure() {
    Entry dir = new DirectoryEntry(null, null, null);
    Entry child1 = new FileEntry(1, "child1", null, null);
    Entry child2 = new DirectoryEntry(2, "child2", null);
    Entry child3 = new FileEntry(3, "child3", null, null);

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
    DirectoryEntry root = new DirectoryEntry(null, "root", null);
    DirectoryEntry dir = new DirectoryEntry(33, "dir", null);
    FileEntry child = new FileEntry(44, "child", null, null);

    root.addChild(root);
    dir.addChild(child);

    Entry renamed = dir.renamed("new dir");

    assertNull(renamed.getParent());
    assertEquals("new dir", renamed.getPath());

    List<Entry> newChildren = renamed.getChildren();
    assertEquals(1, renamed.getChildren().size());

    assertEquals(44, newChildren.get(0).getId());

    assertSame(dir, child.getParent());
    assertSame(renamed, newChildren.get(0).getParent());
  }

  @Test
  public void testNoDifference() {
    DirectoryEntry e1 = new DirectoryEntry(null, "name", null);
    DirectoryEntry e2 = new DirectoryEntry(null, "name", null);

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInName() {
    DirectoryEntry e1 = new DirectoryEntry(null, "name", null);
    DirectoryEntry e2 = new DirectoryEntry(null, "another name", null);

    Difference d = e1.getDifferenceWith(e2);

    assertEquals(MODIFIED, d.getKind());
    assertFalse(d.isFile());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceWithCreatedChild() {
    Entry e1 = new DirectoryEntry(null, "name", null);
    Entry e2 = new DirectoryEntry(null, "name", null);

    Entry child = new FileEntry(1, "name", "content", null);
    e2.addChild(child);

    Difference d = e1.getDifferenceWith(e2);

    assertEquals(NOT_MODIFIED, d.getKind());
    assertEquals(1, d.getChildren().size());

    d = d.getChildren().get(0);

    assertEquals(CREATED, d.getKind());
    assertTrue(d.isFile());
    assertNull(d.getLeft());
    assertSame(child, d.getRight());
  }

  @Test
  public void testDifferenceWithCreatedChildWithSubChildren() {
    Entry dir1 = new DirectoryEntry(null, "name", null);
    Entry dir2 = new DirectoryEntry(null, "name", null);

    Entry subDir = new DirectoryEntry(null, "subDir", null);
    Entry subSubFile = new FileEntry(null, "subSubFile", null, null);

    dir2.addChild(subDir);
    subDir.addChild(subSubFile);

    Difference d = dir1.getDifferenceWith(dir2);

    assertEquals(1, d.getChildren().size());
    d = d.getChildren().get(0);

    assertEquals(CREATED, d.getKind());
    assertFalse(d.isFile());
    assertNull(d.getLeft());
    assertSame(subDir, d.getRight());
    assertEquals(1, d.getChildren().size());

    d = d.getChildren().get(0);

    assertEquals(CREATED, d.getKind());
    assertTrue(d.isFile());
    assertNull(d.getLeft());
    assertSame(subSubFile, d.getRight());
  }

  @Test
  public void testDifferenceWithDeletedChild() {
    Entry dir1 = new DirectoryEntry(null, "name", null);
    Entry dir2 = new DirectoryEntry(null, "name", null);

    Entry subDir = new DirectoryEntry(1, "subDir", null);
    Entry subSubFile = new FileEntry(2, "subSubFile", null, null);

    dir1.addChild(subDir);
    subDir.addChild(subSubFile);

    Difference d = dir1.getDifferenceWith(dir2);

    assertEquals(NOT_MODIFIED, d.getKind());
    assertFalse(d.isFile());
    assertSame(dir1, d.getLeft());
    assertSame(dir2, d.getRight());

    assertEquals(1, d.getChildren().size());
    d = d.getChildren().get(0);

    assertEquals(DELETED, d.getKind());
    assertFalse(d.isFile());
    assertSame(subDir, d.getLeft());
    assertNull(d.getRight());

    assertEquals(1, d.getChildren().size());
    d = d.getChildren().get(0);

    assertEquals(DELETED, d.getKind());
    assertTrue(d.isFile());
    assertSame(subSubFile, d.getLeft());
    assertNull(d.getRight());
  }

  @Test
  public void testDifferenceWithModifiedChild() {
    Entry e1 = new DirectoryEntry(null, "name", null);
    Entry e2 = new DirectoryEntry(null, "name", null);

    Entry child1 = new FileEntry(1, "name1", "content", null);
    Entry child2 = new FileEntry(1, "name2", "content", null);

    e1.addChild(child1);
    e2.addChild(child2);

    Difference d = e1.getDifferenceWith(e2);

    assertEquals(NOT_MODIFIED, d.getKind());
    assertEquals(1, d.getChildren().size());

    d = d.getChildren().get(0);

    assertEquals(MODIFIED, d.getKind());
    assertTrue(d.isFile());
    assertSame(child1, d.getLeft());
    assertSame(child2, d.getRight());
  }

  @Test
  public void testNoesNotIncludeNonModifiedChildDifferences() {
    Entry e1 = new DirectoryEntry(null, "name", null);
    Entry e2 = new DirectoryEntry(null, "name", null);

    e1.addChild(new FileEntry(1, "name", "content", null));
    e2.addChild(new FileEntry(1, "name", "content", null));

    e1.addChild(new FileEntry(2, "another name", "content", null));

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(1, d.getChildren().size());

    assertEquals(DELETED, d.getChildren().get(0).getKind());
  }

  @Test
  public void testDifferenceWithNotModifiedChildWithDifferentIdentity() {
    Entry e1 = new DirectoryEntry(null, "name", null);
    Entry e2 = new DirectoryEntry(null, "name", null);

    e1.addChild(new FileEntry(1, "name", "content", null));
    e2.addChild(new FileEntry(1, "name", "content", null));

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());

    assertEquals(0, d.getChildren().size());
  }

  @Test
  public void testDifferenceWithModifiedBothSubjectAndChild() {
    Entry e1 = new DirectoryEntry(null, "name1", null);
    Entry e2 = new DirectoryEntry(null, "name2", null);

    Entry child1 = new FileEntry(1, "name1", "content", null);
    Entry child2 = new FileEntry(1, "name2", "content", null);

    e1.addChild(child1);
    e2.addChild(child2);

    Difference d = e1.getDifferenceWith(e2);

    assertEquals(MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());

    assertEquals(1, d.getChildren().size());
    d = d.getChildren().get(0);

    assertEquals(MODIFIED, d.getKind());
    assertSame(child1, d.getLeft());
    assertSame(child2, d.getRight());
  }
}
