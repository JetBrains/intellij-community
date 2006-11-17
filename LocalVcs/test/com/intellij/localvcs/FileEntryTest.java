package com.intellij.localvcs;

import static com.intellij.localvcs.Difference.Kind.CREATED;
import static com.intellij.localvcs.Difference.Kind.DELETED;
import static com.intellij.localvcs.Difference.Kind.MODIFIED;
import static com.intellij.localvcs.Difference.Kind.NOT_MODIFIED;
import org.junit.Test;

public class FileEntryTest extends TestCase {
  @Test
  public void testCopying() {
    FileEntry file = new FileEntry(33, "name", "content");

    Entry copy = file.copy();

    assertEquals(33, copy.getId());
    assertEquals("name", copy.getName());
    assertEquals("content", copy.getContent());
  }

  @Test
  public void testDoesNotCopyParent() {
    DirectoryEntry parent = new DirectoryEntry(null, null);
    FileEntry file = new FileEntry(null, null, null);

    parent.addChild(file);

    Entry copy = file.copy();
    assertNull(copy.getParent());
  }

  @Test
  public void testRenaming() {
    DirectoryEntry dir = new DirectoryEntry(null, "dir");
    FileEntry file = new FileEntry(33, "name", "content");

    dir.addChild(file);

    Entry renamed = file.renamed("new name");

    assertEquals(33, renamed.getId());
    assertEquals("new name", renamed.getName());
    assertEquals("content", renamed.getContent());

    assertNull(renamed.getParent());
  }

  @Test
  public void testCanNotWorkWithChildren() {
    FileEntry file = new FileEntry(null, null, null);

    try {
      file.addChild(new FileEntry(null, null, null));
      fail();
    } catch (LocalVcsException e) {}

    try {
      file.removeChild(new FileEntry(null, null, null));
      fail();
    } catch (LocalVcsException e) {}

    try {
      file.getChildren();
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testNoDifference() {
    FileEntry e1 = new FileEntry(null, "name", "content");
    FileEntry e2 = new FileEntry(null, "name", "content");

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInName() {
    FileEntry e1 = new FileEntry(null, "name", "content");
    FileEntry e2 = new FileEntry(null, "another name", "content");

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInContent() {
    FileEntry e1 = new FileEntry(null, "name", "content");
    FileEntry e2 = new FileEntry(null, "name", "another content");

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testAsCreatedDifference() {
    FileEntry e = new FileEntry(null, null, null);

    Difference d = e.asCreatedDifference();

    assertEquals(CREATED, d.getKind());
    assertTrue(d.isFile());
    assertNull(d.getLeft());
    assertSame(e, d.getRight());
  }

  @Test
  public void testAsDeletedDifference() {
    FileEntry e = new FileEntry(null, null, null);

    Difference d = e.asDeletedDifference();

    assertEquals(DELETED, d.getKind());
    assertTrue(d.isFile());
    assertSame(e, d.getLeft());
    assertNull(d.getRight());
  }
}
