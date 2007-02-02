package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class ChangeSetTest extends LocalVcsTestCase {
  private ChangeSet changeSet;
  private ArrayList<Integer> log;

  @Before
  public void setUp() {
    changeSet = cs(new LoggingChange(1), new LoggingChange(2), new LoggingChange(3));
    log = new ArrayList<Integer>();
  }

  @Test
  public void testApplyingIsFIFO() {
    changeSet.applyTo(new RootEntry());
    assertEquals(new Object[]{1, 2, 3}, log);
  }

  @Test
  public void testRevertingIsLIFO() {
    changeSet.revertOn(new RootEntry());
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
