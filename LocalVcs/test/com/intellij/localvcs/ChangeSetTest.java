package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ChangeSetTest extends TestCase {
  private ChangeSet changeSet;
  private ArrayList<Integer> log;

  @Before
  public void setUp() {
    changeSet = cs(new LoggingChange(1),
                   new LoggingChange(2),
                   new LoggingChange(3));
    log = new ArrayList<Integer>();
  }

  @Test
  public void testApplyingIsFIFO() {
    changeSet.applyTo(null);
    assertElements(new Object[]{1, 2, 3}, log);
  }

  @Test
  public void testRevertingIsLIFO() {
    changeSet.revertOn(null);
    assertElements(new Object[]{3, 2, 1}, log);
  }

  private class LoggingChange extends Change {
    private Integer myId;

    public LoggingChange(Integer id) {
      myId = id;
    }

    public void applyTo(RootEntry root) {
      log.add(myId);
    }

    public void revertOn(RootEntry root) {
      log.add(myId);
    }

    public void write(Stream s) throws IOException {
      throw new UnsupportedOperationException();
    }

    protected List<IdPath> getAffectedEntryIdPaths() {
      throw new UnsupportedOperationException();
    }

    public List<Difference> getDifferences(RootEntry r, Entry e) {
      throw new UnsupportedOperationException();
    }
  }
}
