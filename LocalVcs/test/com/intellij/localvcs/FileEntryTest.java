package com.intellij.localvcs;

import static com.intellij.localvcs.Difference.Kind.*;
import org.junit.Test;

public class FileEntryTest extends LocalVcsTestCase {
  @Test
  public void testCopying() {
    FileEntry file = new FileEntry(33, "name", c("content"), 123L);

    Entry copy = file.copy();

    assertEquals(33, copy.getId());
    assertEquals("name", copy.getName());
    assertEquals(c("content"), copy.getContent());
    assertEquals(123L, copy.getTimestamp());
  }

  @Test
  public void testDoesNotCopyParent() {
    DirectoryEntry parent = new DirectoryEntry(null, null, null);
    FileEntry file = new FileEntry(null, null, null, null);

    parent.addChild(file);

    Entry copy = file.copy();
    assertNull(copy.getParent());
  }

  @Test
  public void testRenaming() {
    Entry e = new FileEntry(null, "name", null, null);
    e.changeName("new name");
    assertEquals("new name", e.getName());
  }

  @Test
  public void testNoDifference() {
    FileEntry e1 = new FileEntry(null, "name", c("content"), null);
    FileEntry e2 = new FileEntry(null, "name", c("content"), null);

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInName() {
    Entry e1 = new FileEntry(null, "name", c("content"), null);
    Entry e2 = new FileEntry(null, "another name", c("content"), null);

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    Entry e1 = new FileEntry(null, "name", c(""), null);
    Entry e2 = new FileEntry(null, "NAME", c(""), null);

    Paths.setCaseSensitive(false);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());

    Paths.setCaseSensitive(true);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());
  }

  @Test
  public void testDifferenceInContent() {
    FileEntry e1 = new FileEntry(null, "name", c("content"), null);
    FileEntry e2 = new FileEntry(null, "name", c("another content"), null);

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testAsCreatedDifference() {
    FileEntry e = new FileEntry(null, null, null, null);

    Difference d = e.asCreatedDifference();

    assertEquals(CREATED, d.getKind());
    assertTrue(d.isFile());
    assertNull(d.getLeft());
    assertSame(e, d.getRight());
  }

  @Test
  public void testAsDeletedDifference() {
    FileEntry e = new FileEntry(null, null, null, null);

    Difference d = e.asDeletedDifference();

    assertEquals(DELETED, d.getKind());
    assertTrue(d.isFile());
    assertSame(e, d.getLeft());
    assertNull(d.getRight());
  }
}
