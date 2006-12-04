package com.intellij.localvcs.integration.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.*;
import com.intellij.localvcs.Difference;
import com.intellij.localvcs.DirectoryEntry;
import com.intellij.localvcs.FileEntry;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.integration.ui.DifferenceNode;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.mock.MockFileTypeManager;
import com.intellij.util.Icons;
import org.junit.Test;
import org.junit.Assert;
import org.easymock.EasyMock;

import javax.swing.*;
import java.util.Map;
import java.util.HashMap;

public class DifferenceNodeTest {
  private MyFileTypeManager typeManager = new MyFileTypeManager();



  @Test
  public void testStructure() {
    Difference d = new Difference(false, null, null, null);
    d.addChild(new Difference(false, null, null, null));
    d.getChildren().get(0).addChild(new Difference(true, null, null, null));

    DifferenceNode n = new DifferenceNode(d);
    assertEquals(1, n.getChildCount());
    assertEquals(1, n.getChildAt(0).getChildCount());
  }

  @Test
  public void testName() {
    Entry left = new DirectoryEntry(null, "left", null);
    Entry right = new DirectoryEntry(null, "right", null);

    Difference d = new Difference(false, null, left, right);
    DifferenceNode n = new DifferenceNode(d);

    assertEquals("left", n.getPresentableText(0));
    assertEquals("right", n.getPresentableText(1));
  }

  @Test
  public void testNameForAbsentEntries() {
    Difference d = new Difference(false, null, null, null);
    DifferenceNode n = new DifferenceNode(d);

    assertEquals("", n.getPresentableText(0));
    assertEquals("", n.getPresentableText(1));
  }

  @Test
  public void testIconsForFile() {
    Entry e = new FileEntry(null, "file", null, null);
    DifferenceNode n = new MyDifferenceNode(new Difference(true, null, e, e));
    Icon i = typeManager.addIconForFile("file");

    assertIcons(i, n);
  }

  private void assertIcons(Icon i, DifferenceNode n) {
    assertOpenIcons(i, n);
    assertClosedIcons(i, n);
  }

  @Test
  public void testIconsForFileWhenOneEntryIsNull() {
    Entry e1 = new FileEntry(null, "file1", null, null);
    Entry e2 = new FileEntry(null, "file2", null, null);

    DifferenceNode n1 = new MyDifferenceNode(new Difference(true, null, e1, null));
    DifferenceNode n2 = new MyDifferenceNode(new Difference(true, null, null, e2));

    Icon i1 = typeManager.addIconForFile("file1");
    Icon i2 = typeManager.addIconForFile("file2");

    assertIcons(i1, n1);
    assertIcons(i2, n2);
  }

  @Test
  public void testIconsForDirectory() {
    Entry e = new DirectoryEntry(null, null, null);
    DifferenceNode n = new MyDifferenceNode(new Difference(false, null, e, e));

    assertOpenIcons(Icons.DIRECTORY_OPEN_ICON, n);
    assertClosedIcons(Icons.DIRECTORY_CLOSED_ICON, n);
  }

  private void assertClosedIcons(Icon i, DifferenceNode n) {
    assertSame(i, n.getClosedIcon(0));
    assertSame(i, n.getClosedIcon(1));
  }

  private void assertOpenIcons(Icon i, DifferenceNode n) {
    assertSame(i, n.getOpenIcon(0));
    assertSame(i, n.getOpenIcon(1));
  }

  private class MyDifferenceNode extends DifferenceNode {
    public MyDifferenceNode(Difference d) {
      super(d);
    }

    @Override
    protected FileTypeManager getFileTypeManager() {
      return typeManager;
    }
  }

  private class MyFileTypeManager extends MockFileTypeManager {
    private Map<String, FileType> myTypes = new HashMap<String, FileType>();

    @Override
    public FileType getFileTypeByFileName(String name) {
      return myTypes.get(name);
    }

    public Icon addIconForFile(String name) {
      Icon i = EasyMock.createMock(Icon.class);

      FileType t = EasyMock.createMock(FileType.class);
      EasyMock.expect(t.getIcon()).andStubReturn(i);
      EasyMock.replay(i, t);

      myTypes.put(name, t);
      return i;
    }
  }
}
