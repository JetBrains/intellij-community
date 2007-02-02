package com.intellij.localvcs;

import static com.intellij.localvcs.Difference.Kind.*;
import org.junit.Test;

import java.util.List;

public class DirectoryEntryTest extends LocalVcsTestCase {
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
  public void testAddingExistentChildThrowsException() {
    Entry dir = new DirectoryEntry(null, "dir", null);
    dir.addChild(new FileEntry(null, "child", null, null));

    Paths.setCaseSensitive(true);

    try {
      dir.addChild(new FileEntry(null, "CHILD", null, null));
    }
    catch (RuntimeException e) {
      fail();
    }

    try {
      dir.addChild(new FileEntry(null, "child", null, null));
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry 'child' already exists in 'dir'", e.getMessage());
    }

    Paths.setCaseSensitive(false);

    try {
      dir.addChild(new FileEntry(null, "CHILD", null, null));
      fail();
    }
    catch (RuntimeException e) {
    }
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
  public void testFindChild() {
    Entry dir = new DirectoryEntry(null, null, null);
    Entry one = new FileEntry(null, "one", null, null);
    Entry two = new FileEntry(null, "two", null, null);

    dir.addChild(one);
    dir.addChild(two);

    assertSame(one, dir.findChild("one"));
    assertSame(two, dir.findChild("two"));

    assertNull(dir.findChild("aaa"));

    Paths.setCaseSensitive(true);
    assertNull(dir.findChild("ONE"));

    Paths.setCaseSensitive(false);
    assertSame(one, dir.findChild("ONE"));
  }

  @Test
  public void testChildById() {
    DirectoryEntry dir = new DirectoryEntry(null, null, null);
    Entry file1 = new FileEntry(1, "file1", null, null);
    Entry file2 = new FileEntry(2, "file2", null, null);

    dir.addChild(file1);
    dir.addChild(file2);

    assertSame(file1, dir.findDirectChild(1));
    assertSame(file2, dir.findDirectChild(2));

    assertNull(dir.findDirectChild(99));
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
    Entry dir = new DirectoryEntry(42, "name", 123L);
    Entry copy = dir.copy();

    assertEquals(42, copy.getId());
    assertEquals("name", copy.getPath());
    assertEquals(123L, copy.getTimestamp());
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
    Entry e = new DirectoryEntry(null, "name", null);
    e.changeName("new name");
    assertEquals("new name", e.getName());
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
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    DirectoryEntry e1 = new DirectoryEntry(null, "name", null);
    DirectoryEntry e2 = new DirectoryEntry(null, "NAME", null);

    Paths.setCaseSensitive(false);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());

    Paths.setCaseSensitive(true);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());
  }

  @Test
  public void testDifferenceWithCreatedChild() {
    Entry e1 = new DirectoryEntry(null, "name", null);
    Entry e2 = new DirectoryEntry(null, "name", null);

    Entry child = new FileEntry(1, "name", c("content"), null);
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

    Entry child1 = new FileEntry(1, "name1", c("content"), null);
    Entry child2 = new FileEntry(1, "name2", c("content"), null);

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

    e1.addChild(new FileEntry(1, "name", c("content"), null));
    e2.addChild(new FileEntry(1, "name", c("content"), null));

    e1.addChild(new FileEntry(2, "another name", c("content"), null));

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(1, d.getChildren().size());

    assertEquals(DELETED, d.getChildren().get(0).getKind());
  }

  @Test
  public void testDifferenceWithNotModifiedChildWithDifferentIdentity() {
    Entry e1 = new DirectoryEntry(null, "name", null);
    Entry e2 = new DirectoryEntry(null, "name", null);

    e1.addChild(new FileEntry(1, "name", c("content"), null));
    e2.addChild(new FileEntry(1, "name", c("content"), null));

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

    Entry child1 = new FileEntry(1, "name1", c("content"), null);
    Entry child2 = new FileEntry(1, "name2", c("content"), null);

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
