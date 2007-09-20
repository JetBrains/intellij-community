package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

import java.util.ArrayList;

public class ChangeSetTest extends LocalVcsTestCase {
  ArrayList<Integer> log = new ArrayList<Integer>();

  Entry r = new RootEntry();
  ChangeSet cs = cs(123L, "name", new LoggingChange(1), new LoggingChange(2), new LoggingChange(3));

  @Test
  public void testBuilding() {
    assertEquals(123L, cs.getTimestamp());
    assertEquals("name", cs.getName());
    assertEquals(3, cs.getChanges().size());
  }

  @Test
  public void testApplyingIsFIFO() {
    cs.applyTo(r);
    assertEquals(new Object[]{1, 2, 3}, log);
  }

  @Test
  public void testRevertingIsLIFO() {
    cs.revertOn(r);
    assertEquals(new Object[]{3, 2, 1}, log);
  }

  @Test
  public void testIsCreational() {
    ChangeSet cs1 = cs(new CreateFileChange(1, "file", null, -1, false));
    ChangeSet cs2 = cs(new CreateDirectoryChange(2, "dir"));
    cs1.applyTo(r);
    cs2.applyTo(r);

    assertTrue(cs1.isCreationalFor(r.getEntry("file")));
    assertTrue(cs2.isCreationalFor(r.getEntry("dir")));

    assertFalse(cs1.isCreationalFor(r.getEntry("dir")));
  }

  @Test
  public void testNonCreational() {
    createFile(r, 1, "file", null, -1);
    Entry e = r.getEntry("file");

    cs = cs(new ContentChange("file", null, -1));
    cs.applyTo(r);

    assertFalse(cs.isCreationalFor(e));
  }

  @Test
  public void testCreationalAndNonCreationalInOneChangeSet() {
    cs = cs(new CreateFileChange(1, "file", null, -1, false), new ContentChange("file", null, -1));
    cs.applyTo(r);

    assertTrue(cs.isCreationalFor(r.getEntry("file")));
  }

  @Test
  public void testToCreationalChangesInOneChangeSet() {
    cs = cs(new CreateFileChange(1, "file", null, -1, false), new CreateDirectoryChange(2, "dir"));
    cs.applyTo(r);

    assertTrue(cs.isCreationalFor(r.getEntry("file")));
    assertTrue(cs.isCreationalFor(r.getEntry("dir")));
  }

  @Test
  public void testIsFileContentChange() {
    assertFalse(cs(new CreateFileChange(1, "f", null, -1, false)).isFileContentChange());
    assertTrue(cs(new ContentChange("f", null, -1)).isFileContentChange());
    assertFalse(cs(new ContentChange("f1", null, -1), new ContentChange("f2", null, -1)).isFileContentChange());
    assertFalse(cs(new CreateFileChange(1, "f1", null, -1, false), new ContentChange("f2", null, -1)).isFileContentChange());
  }

  private class LoggingChange extends CreateFileChange {
    private int myId;

    public LoggingChange(int id) {
      super(-1, null, null, -1, false);
      myId = id;
    }

    @Override
    protected IdPath doApplyTo(Entry root) {
      log.add(myId);
      return null;
    }

    @Override
    public void revertOn(Entry root) {
      log.add(myId);
    }
  }
}
