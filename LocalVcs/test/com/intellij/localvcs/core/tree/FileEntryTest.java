package com.intellij.localvcs.core.tree;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.revisions.Difference;
import static com.intellij.localvcs.core.revisions.Difference.Kind.*;
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
    DirectoryEntry parent = new DirectoryEntry(-1, null);
    FileEntry file = new FileEntry(-1, null, null, -1);

    parent.addChild(file);

    Entry copy = file.copy();
    assertNull(copy.getParent());
  }

  @Test
  public void testRenaming() {
    Entry e = new FileEntry(-1, "name", null, -1);
    e.changeName("new name");
    assertEquals("new name", e.getName());
  }

  @Test
  public void testOutdated() {
    Entry e = new FileEntry(-1, "name", null, 2L);

    assertTrue(e.isOutdated(1L));
    assertTrue(e.isOutdated(3L));

    assertFalse(e.isOutdated(2L));
  }


  @Test
  public void testNoDifference() {
    FileEntry e1 = new FileEntry(-1, "name", c("content"), -1);
    FileEntry e2 = new FileEntry(-1, "name", c("content"), -1);

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(NOT_MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInName() {
    Entry e1 = new FileEntry(-1, "name", c("content"), -1);
    Entry e2 = new FileEntry(-1, "another name", c("content"), -1);

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    Entry e1 = new FileEntry(-1, "name", c(""), -1);
    Entry e2 = new FileEntry(-1, "NAME", c(""), -1);

    Paths.setCaseSensitive(false);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());

    Paths.setCaseSensitive(true);
    assertEquals(MODIFIED, e1.getDifferenceWith(e2).getKind());
  }

  @Test
  public void testDifferenceInContent() {
    FileEntry e1 = new FileEntry(-1, "name", c("content"), -1);
    FileEntry e2 = new FileEntry(-1, "name", c("another content"), -1);

    Difference d = e1.getDifferenceWith(e2);
    assertEquals(MODIFIED, d.getKind());
    assertSame(e1, d.getLeft());
    assertSame(e2, d.getRight());
  }

  @Test
  public void testAsCreatedDifference() {
    FileEntry e = new FileEntry(-1, null, null, -1);

    Difference d = e.asCreatedDifference();

    assertEquals(CREATED, d.getKind());
    assertTrue(d.isFile());
    assertNull(d.getLeft());
    assertSame(e, d.getRight());
  }

  @Test
  public void testAsDeletedDifference() {
    FileEntry e = new FileEntry(-1, null, null, -1);

    Difference d = e.asDeletedDifference();

    assertEquals(DELETED, d.getKind());
    assertTrue(d.isFile());
    assertSame(e, d.getLeft());
    assertNull(d.getRight());
  }
}
