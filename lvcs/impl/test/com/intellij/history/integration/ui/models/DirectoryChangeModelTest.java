package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.UnavailableContent;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import org.junit.Test;


public class DirectoryChangeModelTest extends LocalVcsTestCase {
  @Test
  public void testNames() {
    Entry left = new DirectoryEntry(-1, "left");
    Entry right = new DirectoryEntry(-1, "right");

    Difference d = new Difference(false, left, right);
    DirectoryChangeModel m = new DirectoryChangeModel(d);

    assertEquals("left", m.getEntryName(0));
    assertEquals("right", m.getEntryName(1));
  }

  @Test
  public void testNamesForAbsentEntries() {
    Difference d = new Difference(false, null, null);
    DirectoryChangeModel m = new DirectoryChangeModel(d);

    assertEquals("", m.getEntryName(0));
    assertEquals("", m.getEntryName(1));
  }

  @Test
  public void testFileDifferenceModel() {
    Entry left = new FileEntry(-1, "left", null, 123L, false);
    Entry right = new FileEntry(-1, "right", null, 123L, false);

    Difference d = new Difference(false, left, right);
    DirectoryChangeModel dm = new DirectoryChangeModel(d);
    FileDifferenceModel m = dm.getFileDifferenceModel();

    assertTrue(m.getLeftTitle().endsWith("left"));
    assertTrue(m.getRightTitle().endsWith("right"));
  }

  @Test
  public void testCanShowFileDifference() {
    Entry left = new FileEntry(-1, "left", c(""), -1, false);
    Entry right = new FileEntry(-1, "right", c(""), -1, false);

    Difference d1 = new Difference(true, left, right);
    Difference d2 = new Difference(true, null, right);
    Difference d3 = new Difference(true, left, null);

    assertTrue(new DirectoryChangeModel(d1).canShowFileDifference());
    assertFalse(new DirectoryChangeModel(d2).canShowFileDifference());
    assertFalse(new DirectoryChangeModel(d3).canShowFileDifference());
  }

  @Test
  public void testCanNotShowFileDifferenceForDirectories() {
    Entry left = new DirectoryEntry(-1, "left");
    Entry right = new DirectoryEntry(-1, "right");

    Difference d = new Difference(false, left, right);
    assertFalse(new DirectoryChangeModel(d).canShowFileDifference());
  }

  @Test
  public void testCantShowDifferenceIfOneOfFileHasUnavailableContent() {
    Entry e1 = new FileEntry(-1, "one", c("abc"), -1, false);
    Entry e2 = new FileEntry(-1, "two", new UnavailableContent(), -1, false);

    Difference d1 = new Difference(true, e1, e2);
    Difference d2 = new Difference(true, e2, e1);

    assertFalse(new DirectoryChangeModel(d1).canShowFileDifference());
    assertFalse(new DirectoryChangeModel(d2).canShowFileDifference());
  }
}
