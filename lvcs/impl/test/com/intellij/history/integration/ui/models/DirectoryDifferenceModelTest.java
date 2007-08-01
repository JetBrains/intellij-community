package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.UnavailableContent;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import org.junit.Test;


public class DirectoryDifferenceModelTest extends LocalVcsTestCase {
  @Test
  public void testStructure() {
    Difference d = new Difference(false, null, null, null);
    d.addChild(new Difference(false, null, null, null));
    d.getChildren().get(0).addChild(new Difference(true, null, null, null));

    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);
    assertEquals(1, m.getChildren().size());
    assertEquals(1, m.getChildren().get(0).getChildren().size());
  }

  @Test
  public void testNames() {
    Entry left = new DirectoryEntry(-1, "left");
    Entry right = new DirectoryEntry(-1, "right");

    Difference d = new Difference(false, null, left, right);
    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);

    assertEquals("left", m.getEntryName(0));
    assertEquals("right", m.getEntryName(1));
  }

  @Test
  public void testNamesForAbsentEntries() {
    Difference d = new Difference(false, null, null, null);
    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);

    assertEquals("", m.getEntryName(0));
    assertEquals("", m.getEntryName(1));
  }

  @Test
  public void testFileDifferenceModel() {
    Entry left = new FileEntry(-1, "left", null, 123L);
    Entry right = new FileEntry(-1, "right", null, 123L);

    Difference d = new Difference(false, null, left, right);
    DirectoryDifferenceModel dm = new DirectoryDifferenceModel(d);
    FileDifferenceModel m = dm.getFileDifferenceModel();

    assertTrue(m.getLeftTitle().endsWith("left"));
    assertTrue(m.getRightTitle().endsWith("right"));
  }

  @Test
  public void testCanShowFileDifference() {
    Entry left = new FileEntry(-1, "left", c(""), -1);
    Entry right = new FileEntry(-1, "right", c(""), -1);

    Difference d1 = new Difference(true, null, left, right);
    Difference d2 = new Difference(true, null, null, right);
    Difference d3 = new Difference(true, null, left, null);

    assertTrue(new DirectoryDifferenceModel(d1).canShowFileDifference());
    assertFalse(new DirectoryDifferenceModel(d2).canShowFileDifference());
    assertFalse(new DirectoryDifferenceModel(d3).canShowFileDifference());
  }

  @Test
  public void testCanNotShowFileDifferenceForDirectories() {
    Entry left = new DirectoryEntry(-1, "left");
    Entry right = new DirectoryEntry(-1, "right");

    Difference d = new Difference(false, null, left, right);
    assertFalse(new DirectoryDifferenceModel(d).canShowFileDifference());
  }

  @Test
  public void testCantShowDifferenceIfOneOfFileHasUnavailableContent() {
    Entry e1 = new FileEntry(-1, "one", c("abc"), -1);
    Entry e2 = new FileEntry(-1, "two", new UnavailableContent(), -1);

    Difference d1 = new Difference(true, null, e1, e2);
    Difference d2 = new Difference(true, null, e2, e1);

    assertFalse(new DirectoryDifferenceModel(d1).canShowFileDifference());
    assertFalse(new DirectoryDifferenceModel(d2).canShowFileDifference());
  }
}
