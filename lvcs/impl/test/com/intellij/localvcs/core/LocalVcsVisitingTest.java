package com.intellij.localvcs.core;

import com.intellij.localvcs.core.changes.*;
import org.junit.Test;

public class LocalVcsVisitingTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testSimpleVisit() throws Exception {
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");

    assertVisitorLog("changeSet createEntry changeSet createEntry ");
  }

  @Test
  public void testVisitChangeSet() throws Exception {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);

    assertVisitorLog("changeSet createEntry createEntry ");
  }

  @Test
  public void testVisitingChangesInNotFinishedChangeSet() throws Exception {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");

    assertVisitorLog("changeSet createEntry createEntry ");
  }

  @Test
  public void testVisitingAllChanges() throws Exception {
    vcs.createFile("f", null, -1);
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);
    vcs.beginChangeSet();
    vcs.rename("dir", "newDir");

    assertVisitorLog("changeSet rename changeSet createEntry changeSet createEntry ");
  }

  @Test
  public void testStop() throws Exception {
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");

    TestVisitor visitor = new TestVisitor() {
      int count = 0;

      @Override
      public void visit(final ChangeSet c) throws StopVisitingException {
        if (++count == 2) stop();
        super.visit(c);
      }
    };

    vcs.accept(visitor);
    assertEquals("changeSet createEntry ", visitor.getLog());
  }

  private void assertVisitorLog(final String expected) throws Exception {
    TestVisitor visitor = new TestVisitor();
    vcs.accept(visitor);
    assertEquals(expected, visitor.getLog());
  }

  private class TestVisitor extends ChangeVisitor {
    private String log = "";

    @Override
    public void visit(ChangeSet c) throws StopVisitingException {
      log += "changeSet ";
    }

    @Override
    public void visit(CreateEntryChange c) {
      log += "createEntry ";
    }

    @Override
    public void visit(ChangeFileContentChange c) {
    }

    @Override
    public void visit(RenameChange c) {
      log += "rename ";
    }

    @Override
    public void visit(MoveChange c) {
    }

    @Override
    public void visit(DeleteChange c) {
    }

    public String getLog() {
      return log;
    }
  }
}
