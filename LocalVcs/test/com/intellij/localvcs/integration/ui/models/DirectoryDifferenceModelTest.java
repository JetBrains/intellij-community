package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.*;
import com.intellij.localvcs.integration.stubs.StubFileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.Icons;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Test;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;


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
    Entry left = new DirectoryEntry(null, "left", null);
    Entry right = new DirectoryEntry(null, "right", null);

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
    Entry left = new DirectoryEntry(null, "left", 123L);
    Entry right = new DirectoryEntry(null, "right", 123L);

    Difference d = new Difference(false, null, left, right);
    DirectoryDifferenceModel nm = new DirectoryDifferenceModel(d);
    FileDifferenceModel m = nm.getFileDifferenceModel();

    assertTrue(m.getLeftTitle().endsWith("left"));
    assertTrue(m.getRightTitle().endsWith("right"));
  }

  @Test
  public void testCanShowFileDifference() {
    Entry left = new FileEntry(null, "left", null, null);
    Entry right = new FileEntry(null, "right", null, null);

    Difference d1 = new Difference(true, null, left, right);
    Difference d2 = new Difference(true, null, null, right);
    Difference d3 = new Difference(true, null, left, null);

    assertTrue(new DirectoryDifferenceModel(d1).canShowFileDifference());
    assertFalse(new DirectoryDifferenceModel(d2).canShowFileDifference());
    assertFalse(new DirectoryDifferenceModel(d3).canShowFileDifference());
  }

  @Test
  public void testCanNotShowFileDifferenceForDirectories() {
    Entry left = new DirectoryEntry(null, "left", null);
    Entry right = new DirectoryEntry(null, "right", null);

    Difference d = new Difference(false, null, left, right);
    assertFalse(new DirectoryDifferenceModel(d).canShowFileDifference());
  }

  @Test
  public void testIconsForFile() {
    Entry e = new FileEntry(null, "file", null, null);
    Difference d = new Difference(true, null, e, e);
    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);

    MyFileTypeManager tm = new MyFileTypeManager();
    Icon i = tm.addIconForFile("file");

    assertIcons(i, i, m, tm);
  }

  @Test
  public void testIconsForDifferentFiles() {
    Entry e1 = new FileEntry(null, "one", null, null);
    Entry e2 = new FileEntry(null, "two", null, null);

    Difference d = new Difference(true, null, e1, e2);
    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);

    MyFileTypeManager tm = new MyFileTypeManager();
    Icon i1 = tm.addIconForFile("one");
    Icon i2 = tm.addIconForFile("two");

    assertIcons(i1, i2, m, tm);
  }

  @Test
  public void testIconsForFileWhenOneEntryIsNull() {
    Entry e1 = new FileEntry(null, "file1", null, null);
    Entry e2 = new FileEntry(null, "file2", null, null);

    Difference d1 = new Difference(true, null, e1, null);
    Difference d2 = new Difference(true, null, null, e2);

    DirectoryDifferenceModel m1 = new DirectoryDifferenceModel(d1);
    DirectoryDifferenceModel m2 = new DirectoryDifferenceModel(d2);

    MyFileTypeManager tm = new MyFileTypeManager();
    Icon i1 = tm.addIconForFile("file1");
    Icon i2 = tm.addIconForFile("file2");

    assertIcons(i1, null, m1, tm);
    assertIcons(null, i2, m2, tm);
  }

  @Test
  public void testIconsForDirectory() {
    Entry e = new DirectoryEntry(null, null, null);
    Difference d = new Difference(false, null, e, e);
    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);

    Icon open = Icons.DIRECTORY_OPEN_ICON;
    Icon closed = Icons.DIRECTORY_CLOSED_ICON;
    assertOpenIcons(open, open, m, null);
    assertClosedIcons(closed, closed, m, null);
  }

  @Test
  public void testIconsForDirectoryWhenOneEntryIsNull() {
    Entry e = new DirectoryEntry(null, null, null);
    Difference d = new Difference(false, null, e, null);
    DirectoryDifferenceModel m = new DirectoryDifferenceModel(d);

    Icon open = Icons.DIRECTORY_OPEN_ICON;
    Icon closed = Icons.DIRECTORY_CLOSED_ICON;
    assertOpenIcons(open, open, m, null);
    assertClosedIcons(closed, closed, m, null);
  }

  private void assertIcons(Icon left, Icon right, DirectoryDifferenceModel m, FileTypeManager tm) {
    assertOpenIcons(left, right, m, tm);
    assertClosedIcons(left, right, m, tm);
  }

  private void assertClosedIcons(Icon left, Icon right, DirectoryDifferenceModel m, FileTypeManager tm) {
    assertSame(left, m.getClosedIcon(0, tm));
    assertSame(right, m.getClosedIcon(1, tm));
  }

  private void assertOpenIcons(Icon left, Icon right, DirectoryDifferenceModel m, FileTypeManager tm) {
    assertSame(left, m.getOpenIcon(0, tm));
    assertSame(right, m.getOpenIcon(1, tm));
  }

  private class MyFileTypeManager extends StubFileTypeManager {
    private Map<String, FileType> myTypes = new HashMap<String, FileType>();

    @Override
    public FileType getFileTypeByFileName(String name) {
      return myTypes.get(name);
    }

    public Icon addIconForFile(String name) {
      Icon i = createMock(Icon.class);

      FileType t = createMock(FileType.class);
      expect(t.getIcon()).andStubReturn(i);
      replay(i, t);

      myTypes.put(name, t);
      return i;
    }
  }
}
