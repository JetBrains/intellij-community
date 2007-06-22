package com.intellij.history.core.tree;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import static com.intellij.history.core.revisions.Difference.Kind.*;
import com.intellij.history.core.storage.UnavailableContent;
import org.junit.Test;

import java.util.List;

public class DirectoryEntryTest extends LocalVcsTestCase {
  @Test
  public void testAddingChildren() {
    Entry dir = new DirectoryEntry(-1, null);
    Entry file = new FileEntry(-1, null, null, -1);

    dir.addChild(file);

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));

    assertSame(dir, file.getParent());
  }

  @Test
  public void testAddingExistentChildThrowsException() {
    Entry dir = new DirectoryEntry(-1, "dir");
    dir.addChild(new FileEntry(-1, "child", null, -1));

    Paths.setCaseSensitive(true);

    try {
      dir.addChild(new FileEntry(-1, "CHILD", null, -1));
    }
    catch (RuntimeException e) {
      fail();
    }

    try {
      dir.addChild(new FileEntry(-1, "child", null, -1));
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry 'child' already exists in 'dir'", e.getMessage());
    }

    Paths.setCaseSensitive(false);

    try {
      dir.addChild(new FileEntry(-1, "CHILD", null, -1));
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRemovingChildren() {
    Entry dir = new DirectoryEntry(-1, null);
    Entry file = new FileEntry(-1, null, null, -1);

    dir.addChild(file);
    assertFalse(dir.getChildren().isEmpty());

    dir.removeChild(file);
    assertTrue(dir.getChildren().isEmpty());
    assertNull(file.getParent());
  }

  @Test
  public void testFindChild() {
    Entry dir = new DirectoryEntry(-1, null);
    Entry one = new FileEntry(-1, "one", null, -1);
    Entry two = new FileEntry(-1, "two", null, -1);

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
    DirectoryEntry dir = new DirectoryEntry(-1, null);
    Entry file1 = new FileEntry(1, "file1", null, -1);
    Entry file2 = new FileEntry(2, "file2", null, -1);

    dir.addChild(file1);
    dir.addChild(file2);

    assertSame(file1, dir.findDirectChild(1));
    assertSame(file2, dir.findDirectChild(2));

    assertNull(dir.findDirectChild(99));
  }

  @Test
  public void testIdPath() {
    Entry dir = new DirectoryEntry(1, null);
    Entry file = new FileEntry(2, null, null, -1);

    dir.addChild(file);

    assertEquals(idp(1, 2), file.getIdPath());
  }

  @Test
  public void testPath() {
    Entry dir = new DirectoryEntry(-1, "dir");
    Entry file = new FileEntry(-1, "file", null, -1);

    dir.addChild(file);

    assertEquals("dir/file", file.getPath());
  }

  @Test
  public void testPathWithoutParent() {
    assertEquals("dir", new DirectoryEntry(-1, "dir").getPath());
    assertEquals("file", new FileEntry(-1, "file", null, -1).getPath());
  }

  @Test
  public void testCopyingWithContent() {
    Entry dir = new DirectoryEntry(42, "name");
    Entry copy = dir.copy();

    assertEquals(42, copy.getId());
    assertEquals("name", copy.getPath());
  }

  @Test
  public void testDoesNotCopyParent() {
    Entry parent = new DirectoryEntry(-1, null);
    Entry dir = new DirectoryEntry(-1, null);

    parent.addChild(dir);

    assertNull(dir.copy().getParent());
  }

  @Test
  public void testCopyingContentRecursively() {
    Entry dir = new DirectoryEntry(-1, null);
    Entry child1 = new FileEntry(1, "child1", null, -1);
    Entry child2 = new DirectoryEntry(2, "child2");
    Entry child3 = new FileEntry(3, "child3", null, -1);

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
    Entry dir = new DirectoryEntry(-1, null);
    Entry child1 = new FileEntry(1, "child1", null, -1);
    Entry child2 = new DirectoryEntry(2, "child2");
    Entry child3 = new FileEntry(3, "child3", null, -1);

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
    Entry e = new DirectoryEntry(-1, "name");
    e.changeName("new name");
    assertEquals("new name", e.getName());
  }

  @Test
  public void testRenamingChildToNonExistentNameDoesNotThrowException() {
    Entry dir = new DirectoryEntry(0, "dir");
    Entry child = new FileEntry(1, "child", null, -1);
    dir.addChild(child);

    child.changeName("new name");

    assertEquals("new name", child.getName());
  }

  @Test
  public void testRenamingChildToExistingNameThrowsException() {
    Entry dir = new DirectoryEntry(0, "dir");
    Entry child1 = new FileEntry(1, "child1", null, -1);
    Entry child2 = new FileEntry(2, "child2", null, -1);
    dir.addChild(child1);
    dir.addChild(child2);

    try {
      child1.changeName("child2");
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testHasUnavailableContent() {
    Entry dir = new DirectoryEntry(-1, "dir");
    assertFalse(dir.hasUnavailableContent());

    dir.addChild(new FileEntry(1, "f", c("abc"), -1));
    assertFalse(dir.hasUnavailableContent());

    DirectoryEntry subDir = new DirectoryEntry(-1, "subDir");
    subDir.addChild(new FileEntry(1, "f", new UnavailableContent(), -1));
    dir.addChild(subDir);

    assertTrue(dir.hasUnavailableContent());
    assertTrue(subDir.hasUnavailableContent());
  }

  @Test
  public void testNoDifference() {
    DirectoryEntry e1 = new DirectoryEntry(-1, "name");
    DirectoryEntry e2 = new DirectoryEntry(-1, "name");

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInName() {
    DirectoryEntry e1 = new DirectoryEntry(-1, "name");
    DirectoryEntry e2 = new DirectoryEntry(-1, "another name");

    Difference d = e1.getDifferenceWith(e2);

    assertEquals(MODIFIED, d.getKind());
    assertFalse(d.isFile());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    DirectoryEntry e1 = new DirectoryEntry(-1, "name");
    DirectoryEntry e2 = new DirectoryEntry(-1, "NAME");

    Paths.setCaseSensitive(false);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());

    Paths.setCaseSensitive(true);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());
  }

  @Test
  public void testDifferenceWithCreatedChild() {
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    Entry child = new FileEntry(1, "name", c("content"), -1);
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
    Entry dir1 = new DirectoryEntry(-1, "name");
    Entry dir2 = new DirectoryEntry(-1, "name");

    Entry subDir = new DirectoryEntry(-1, "subDir");
    Entry subSubFile = new FileEntry(-1, "subSubFile", null, -1);

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
    Entry dir1 = new DirectoryEntry(-1, "name");
    Entry dir2 = new DirectoryEntry(-1, "name");

    Entry subDir = new DirectoryEntry(1, "subDir");
    Entry subSubFile = new FileEntry(2, "subSubFile", null, -1);

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
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    Entry child1 = new FileEntry(1, "name1", c("content"), -1);
    Entry child2 = new FileEntry(1, "name2", c("content"), -1);

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
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    e1.addChild(new FileEntry(1, "name", c("content"), -1));
    e2.addChild(new FileEntry(1, "name", c("content"), -1));

    e1.addChild(new FileEntry(2, "another name", c("content"), -1));

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(1, d.getChildren().size());

    assertEquals(DELETED, d.getChildren().get(0).getKind());
  }

  @Test
  public void testDifferenceWithNotModifiedChildWithDifferentIdentity() {
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    e1.addChild(new FileEntry(1, "name", c("content"), -1));
    e2.addChild(new FileEntry(1, "name", c("content"), -1));

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());

    assertEquals(0, d.getChildren().size());
  }

  @Test
  public void testDifferenceWithModifiedBothSubjectAndChild() {
    Entry e1 = new DirectoryEntry(-1, "name1");
    Entry e2 = new DirectoryEntry(-1, "name2");

    Entry child1 = new FileEntry(1, "name1", c("content"), -1);
    Entry child2 = new FileEntry(1, "name2", c("content"), -1);

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
