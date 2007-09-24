package com.intellij.history.core.tree;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.UnavailableContent;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

public class DirectoryEntryTest extends LocalVcsTestCase {
  @Test
  public void testAddingChildren() {
    Entry dir = new DirectoryEntry(-1, null);
    Entry file = new FileEntry(-1, null, null, -1, false);

    dir.addChild(file);

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));

    assertSame(dir, file.getParent());
  }

  @Test
  public void testAddingExistentChildThrowsException() {
    Entry dir = new DirectoryEntry(-1, "dir");
    dir.addChild(new FileEntry(-1, "child", null, -1, false));

    Paths.setCaseSensitive(true);

    try {
      dir.addChild(new FileEntry(-1, "CHILD", null, -1, false));
    }
    catch (RuntimeException e) {
      fail();
    }

    try {
      dir.addChild(new FileEntry(-1, "child", null, -1, false));
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry 'child' already exists in 'dir'", e.getMessage());
    }

    Paths.setCaseSensitive(false);

    try {
      dir.addChild(new FileEntry(-1, "CHILD", null, -1, false));
      fail();
    }
    catch (RuntimeException e) {
    }
  }

  @Test
  public void testRemovingChildren() {
    Entry dir = new DirectoryEntry(-1, null);
    Entry file = new FileEntry(-1, null, null, -1, false);

    dir.addChild(file);
    assertFalse(dir.getChildren().isEmpty());

    dir.removeChild(file);
    assertTrue(dir.getChildren().isEmpty());
    assertNull(file.getParent());
  }

  @Test
  public void testFindChild() {
    Entry dir = new DirectoryEntry(-1, null);
    Entry one = new FileEntry(-1, "one", null, -1, false);
    Entry two = new FileEntry(-1, "two", null, -1, false);

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
    Entry file1 = new FileEntry(1, "file1", null, -1, false);
    Entry file2 = new FileEntry(2, "file2", null, -1, false);

    dir.addChild(file1);
    dir.addChild(file2);

    assertSame(file1, dir.findDirectChild(1));
    assertSame(file2, dir.findDirectChild(2));

    assertNull(dir.findDirectChild(99));
  }

  @Test
  public void testIdPath() {
    Entry dir = new DirectoryEntry(1, null);
    Entry file = new FileEntry(2, null, null, -1, false);

    dir.addChild(file);

    assertEquals(idp(1, 2), file.getIdPath());
  }

  @Test
  public void testPath() {
    Entry dir = new DirectoryEntry(-1, "dir");
    Entry file = new FileEntry(-1, "file", null, -1, false);

    dir.addChild(file);

    assertEquals("dir/file", file.getPath());
  }

  @Test
  public void testPathWithoutParent() {
    assertEquals("dir", new DirectoryEntry(-1, "dir").getPath());
    assertEquals("file", new FileEntry(-1, "file", null, -1, false).getPath());
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
    Entry child1 = new FileEntry(1, "child1", null, -1, false);
    Entry child2 = new DirectoryEntry(2, "child2");
    Entry child3 = new FileEntry(3, "child3", null, -1, false);

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
    Entry child1 = new FileEntry(1, "child1", null, -1, false);
    Entry child2 = new DirectoryEntry(2, "child2");
    Entry child3 = new FileEntry(3, "child3", null, -1, false);

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
    Entry child = new FileEntry(1, "child", null, -1, false);
    dir.addChild(child);

    child.changeName("new name");

    assertEquals("new name", child.getName());
  }

  @Test
  public void testRenamingChildToExistingNameThrowsException() {
    Entry dir = new DirectoryEntry(0, "dir");
    Entry child1 = new FileEntry(1, "child1", null, -1, false);
    Entry child2 = new FileEntry(2, "child2", null, -1, false);
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

    assertHasNoUnavailableContent(dir);

    dir.addChild(new FileEntry(1, "f", c("abc"), -1, false));
    assertHasNoUnavailableContent(dir);

    FileEntry f1 = new FileEntry(2, "f1", new UnavailableContent(), -1, false);
    FileEntry f2 = new FileEntry(3, "f2", new UnavailableContent(), -1, false);

    DirectoryEntry subDir = new DirectoryEntry(-1, "subDir");
    dir.addChild(subDir);
    dir.addChild(f1);
    subDir.addChild(f2);

    assertHasUnavailableContent(dir, f2, f1);
    assertHasUnavailableContent(subDir, f2);
  }

  private void assertHasNoUnavailableContent(Entry dir) {
    List<Entry> ee = new ArrayList<Entry>();
    assertFalse(dir.hasUnavailableContent(ee));
    assertTrue(ee.isEmpty());
  }

  private void assertHasUnavailableContent(Entry dir, Entry... entries) {
    List<Entry> ee = new ArrayList<Entry>();

    assertTrue(dir.hasUnavailableContent(ee));
    assertEquals(entries, ee);
  }

  @Test
  public void testNoDifference() {
    DirectoryEntry e1 = new DirectoryEntry(-1, "name");
    DirectoryEntry e2 = new DirectoryEntry(-1, "name");

    assertTrue(e1.getDifferencesWith(e2).isEmpty());
  }

  @Test
  public void testDifferenceInName() {
    DirectoryEntry e1 = new DirectoryEntry(-1, "name");
    DirectoryEntry e2 = new DirectoryEntry(-1, "another name");

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertEquals(1, dd.size());
    assertDirDifference(dd.get(0), e1, e2);
  }

  @Test
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    DirectoryEntry e1 = new DirectoryEntry(-1, "name");
    DirectoryEntry e2 = new DirectoryEntry(-1, "NAME");

    Paths.setCaseSensitive(false);
    assertEquals(1, e1.getDifferencesWith(e2).size());

    Paths.setCaseSensitive(true);
    assertEquals(1, e1.getDifferencesWith(e2).size());
  }

  @Test
  public void testDifferenceWithCreatedChild() {
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    Entry child = new FileEntry(1, "name", c("content"), -1, false);
    e2.addChild(child);

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertEquals(1, dd.size());
    assertFileDifference(dd.get(0), null, child);
  }

  @Test
  public void testDifferenceWithCreatedChildWithSubChildren() {
    Entry dir1 = new DirectoryEntry(-1, "name");
    Entry dir2 = new DirectoryEntry(-1, "name");

    Entry subDir = new DirectoryEntry(-1, "subDir");
    Entry subSubFile = new FileEntry(-1, "subSubFile", null, -1, false);

    dir2.addChild(subDir);
    subDir.addChild(subSubFile);

    List<Difference> dd = dir1.getDifferencesWith(dir2);
    assertEquals(2, dd.size());
    assertDirDifference(dd.get(0), null, subDir);
    assertFileDifference(dd.get(1), null, subSubFile);
  }

  @Test
  public void testDifferenceWithDeletedChild() {
    Entry dir1 = new DirectoryEntry(-1, "name");
    Entry dir2 = new DirectoryEntry(-1, "name");

    Entry subDir = new DirectoryEntry(1, "subDir");
    Entry subSubFile = new FileEntry(2, "subSubFile", null, -1, false);

    dir1.addChild(subDir);
    subDir.addChild(subSubFile);

    List<Difference> dd = dir1.getDifferencesWith(dir2);
    assertEquals(2, dd.size());
    assertDirDifference(dd.get(0), subDir, null);
    assertFileDifference(dd.get(1), subSubFile, null);
  }

  @Test
  public void testDifferenceWithModifiedChild() {
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    Entry child1 = new FileEntry(1, "name1", c("content"), -1, false);
    Entry child2 = new FileEntry(1, "name2", c("content"), -1, false);

    e1.addChild(child1);
    e2.addChild(child2);

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertEquals(1, dd.size());
    assertFileDifference(dd.get(0), child1, child2);
  }

  @Test
  public void testNoesNotIncludeNonModifiedChildDifferences() {
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    e1.addChild(new FileEntry(1, "name", c("content"), -1, false));
    e1.addChild(new FileEntry(2, "another name", c("content"), -1, false));

    e2.addChild(new FileEntry(1, "name", c("content"), -1, false));

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertEquals("another name", dd.get(0).getLeft().getName());
    assertEquals(null, dd.get(0).getRight());
  }

  @Test
  public void testDifferenceWithNotModifiedChildWithDifferentIdentity() {
    Entry e1 = new DirectoryEntry(-1, "name");
    Entry e2 = new DirectoryEntry(-1, "name");

    e1.addChild(new FileEntry(1, "name", c("content"), -1, false));
    e2.addChild(new FileEntry(1, "name", c("content"), -1, false));

    assertTrue(e1.getDifferencesWith(e2).isEmpty());
  }

  @Test
  public void testDifferenceWithModifiedBothSubjectAndChild() {
    Entry e1 = new DirectoryEntry(-1, "name1");
    Entry e2 = new DirectoryEntry(-1, "name2");

    Entry child1 = new FileEntry(1, "name1", c("content"), -1, false);
    Entry child2 = new FileEntry(1, "name2", c("content"), -1, false);

    e1.addChild(child1);
    e2.addChild(child2);

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertEquals(2, dd.size());

    assertDirDifference(dd.get(0), e1, e2);
    assertFileDifference(dd.get(1), child1, child2);
  }

  @Test
  public void testIncludesDifferenceForChilderWhenParentWasModified() {
    Entry dir1 = new DirectoryEntry(1, "dir1");
    Entry dir2 = new DirectoryEntry(1, "dir2");

    Entry subDir1 = new DirectoryEntry(2, "subDir");
    Entry subDir2 = new DirectoryEntry(2, "subDir");

    Entry child1 = new FileEntry(3, "name", c("content"), -1, false);
    Entry child2 = new FileEntry(3, "name", c("content"), -1, false);

    dir1.addChild(subDir1);
    dir2.addChild(subDir2);
    subDir1.addChild(child1);
    subDir2.addChild(child2);

    List<Difference> dd = dir1.getDifferencesWith(dir2);
    assertEquals(3, dd.size());

    assertDirDifference(dd.get(0), dir1, dir2);
    assertDirDifference(dd.get(1), subDir1, subDir2);
    assertFileDifference(dd.get(2), child1, child2);
  }

  private void assertDirDifference(Difference d, Entry left, Entry right) {
    assertDifference(d, left, right, false);
  }

  private void assertFileDifference(Difference d, Entry left, Entry right) {
    assertDifference(d, left, right, true);
  }

  private void assertDifference(Difference d, Entry left, Entry right, boolean isFile) {
    assertEquals(isFile, d.isFile());
    assertSame(left, d.getLeft());
    assertSame(right, d.getRight());
  }
}
