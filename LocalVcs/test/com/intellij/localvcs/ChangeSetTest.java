package com.intellij.localvcs;

import org.junit.Test;

import java.util.ArrayList;

public class ChangeSetTest extends LocalVcsTestCase {
  private ArrayList<Integer> log = new ArrayList<Integer>();
  private ChangeSet cs = cs(123L, "name", new LoggingChange(1), new LoggingChange(2), new LoggingChange(3));

  @Test
  public void testBuilding() {
    assertEquals(123L, cs.getTimestamp());
    assertEquals("name", cs.getLabel());
    assertEquals(3, cs.getChanges().size());
  }

  @Test
  public void testApplyingIsFIFO() {
    cs.applyTo(new RootEntry());
    assertEquals(new Object[]{1, 2, 3}, log);
  }

  @Test
  public void testRevertingIsLIFO() {
    cs.revertOn(new RootEntry());
    assertEquals(new Object[]{3, 2, 1}, log);
  }

  private class LoggingChange extends CreateFileChange {
    private Integer myId;

    public LoggingChange(Integer id) {
      super(null, null, null, null);
      myId = id;
    }

    public void applyTo(RootEntry root) {
      log.add(myId);
    }

    public void revertOn(RootEntry root) {
      log.add(myId);
    }
  }
}
