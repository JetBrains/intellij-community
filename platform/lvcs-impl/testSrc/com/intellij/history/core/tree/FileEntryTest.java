package com.intellij.history.core.tree;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.UnavailableContent;
import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

public class FileEntryTest extends LocalVcsTestCase {
  @Test
  public void testHasUnavailableContent() {
    Entry e1 = new FileEntry(1, null, c("abc"), -1, false);
    Entry e2 = new FileEntry(2, null, new UnavailableContent(), -1, false);

    assertFalse(e1.hasUnavailableContent());
    assertTrue(e2.hasUnavailableContent());
  }

  @Test
  public void testCopying() {
    FileEntry file = new FileEntry(33, "name", c("content"), 123L, true);

    Entry copy = file.copy();

    assertEquals(33, copy.getId());
    assertEquals("name", copy.getName());
    assertEquals(c("content"), copy.getContent());
    assertEquals(123L, copy.getTimestamp());
    assertTrue(copy.isReadOnly());
  }

  @Test
  public void testDoesNotCopyParent() {
    DirectoryEntry parent = new DirectoryEntry(-1, null);
    FileEntry file = new FileEntry(-1, null, null, -1, false);

    parent.addChild(file);

    Entry copy = file.copy();
    assertNull(copy.getParent());
  }

  @Test
  public void testRenaming() {
    Entry e = new FileEntry(-1, "name", null, -1, false);
    e.changeName("new name");
    assertEquals("new name", e.getName());
  }

  @Test
  public void testOutdated() {
    Entry e = new FileEntry(-1, "name", null, 2L, false);

    assertTrue(e.isOutdated(1L));
    assertTrue(e.isOutdated(3L));

    assertFalse(e.isOutdated(2L));
  }

  @Test
  public void testNoDifference() {
    FileEntry e1 = new FileEntry(-1, "name", c("content"), -1, false);
    FileEntry e2 = new FileEntry(-1, "name", c("content"), -1, false);

    assertTrue(e1.getDifferencesWith(e2).isEmpty());
  }

  @Test
  public void testDifferenceInName() {
    Entry e1 = new FileEntry(-1, "name", c("content"), -1, false);
    Entry e2 = new FileEntry(-1, "another name", c("content"), -1, false);

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertDifference(dd, e1, e2);
  }

  @Test
  public void testDifferenceInNameIsAlwaysCaseSensitive() {
    Entry e1 = new FileEntry(-1, "name", c(""), -1, false);
    Entry e2 = new FileEntry(-1, "NAME", c(""), -1, false);

    Paths.setCaseSensitive(false);
    assertEquals(1, e1.getDifferencesWith(e2).size());

    Paths.setCaseSensitive(true);
    assertEquals(1, e1.getDifferencesWith(e2).size());
  }

  @Test
  public void testDifferenceInContent() {
    FileEntry e1 = new FileEntry(-1, "name", c("content"), -1, false);
    FileEntry e2 = new FileEntry(-1, "name", c("another content"), -1, false);

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertDifference(dd, e1, e2);
  }
  
  @Test
  public void testDifferenceInROStatus() {
    FileEntry e1 = new FileEntry(-1, "name", c("content"), -1, true);
    FileEntry e2 = new FileEntry(-1, "name", c("content"), -1, false);

    List<Difference> dd = e1.getDifferencesWith(e2);
    assertDifference(dd, e1, e2);
  }

  @Test
  public void testAsCreatedDifference() {
    FileEntry e = new FileEntry(-1, null, null, -1, false);

    ArrayList<Difference> dd = new ArrayList<Difference>();
    e.collectCreatedDifferences(dd);
    assertDifference(dd, null, e);
  }

  @Test
  public void testAsDeletedDifference() {
    FileEntry e = new FileEntry(-1, null, null, -1, false);

    ArrayList<Difference> dd = new ArrayList<Difference>();
    e.collectDeletedDifferences(dd);
    assertDifference(dd, e, null);
  }

  private void assertDifference(List<Difference> dd, Entry left, Entry right) {
    assertEquals(1, dd.size());
    Difference d = dd.get(0);

    assertTrue(d.isFile());
    assertSame(left, d.getLeft());
    assertSame(right, d.getRight());
  }
}
