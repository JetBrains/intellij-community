package com.intellij.history.core;

import com.intellij.history.core.changes.*;
import com.intellij.history.core.tree.Entry;
import org.junit.Test;

public class LocalVcsVisitingTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testSimpleVisit() throws Exception {
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    vcs.createDirectory("dir");

    assertVisitorLog("started begin create end begin create end finished ");
  }

  @Test
  public void testVisitChangeSet() throws Exception {
    vcs.beginChangeSet();
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);

    assertVisitorLog("started begin create create end finished ");
  }

  @Test
  public void testVisitingChangesInNotFinishedChangeSet() throws Exception {
    vcs.beginChangeSet();
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    vcs.createDirectory("dir");

    assertVisitorLog("started begin create create end finished ");
  }

  @Test
  public void testVisitingAllChanges() throws Exception {
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);
    vcs.beginChangeSet();
    vcs.rename("dir", "newDir");

    assertVisitorLog("started begin rename end begin create end begin create end finished ");
  }

  @Test
  public void testStop() throws Exception {
    long timestamp = -1;
    vcs.createFile("f", null, timestamp, false);
    vcs.createDirectory("dir");

    TestVisitor visitor = new TestVisitor() {
      int count = 0;

      @Override
      public void begin(ChangeSet c) throws StopVisitingException {
        if (++count == 2) stop();
        super.begin(c);
      }
    };

    vcs.accept(visitor);
    assertEquals("started begin create end finished ", visitor.getLog());
  }

  private void assertVisitorLog(final String expected) throws Exception {
    TestVisitor visitor = new TestVisitor();
    vcs.accept(visitor);
    assertEquals(expected, visitor.getLog());
  }

  private class TestVisitor extends ChangeVisitor {
    private String log = "";

    @Override
    public void begin(ChangeSet c) throws StopVisitingException {
      log += "begin ";
    }

    @Override
    public void end(ChangeSet c) throws StopVisitingException {
      log += "end ";
    }

    @Override
    public void visit(CreateEntryChange c) {
      log += "create ";
    }

    @Override
    public void visit(RenameChange c) {
      log += "rename ";
    }

    @Override
    public void started(Entry root) {
      log += "started ";
    }

    @Override
    public void finished() {
      log += "finished ";
    }

    public String getLog() {
      return log;
    }
  }
}
