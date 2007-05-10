package com.intellij.localvcs.core;

import com.intellij.localvcs.core.changes.*;
import org.junit.Test;

public class LocalVcsVisitingTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testSimpleVisit() throws Exception {
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");

    assertVisitorLog("createDir createFile ");
  }

  @Test
  public void testVisitChangeSet() throws Exception {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);

    assertVisitorLog("createDir createFile ");
  }

  @Test
  public void testVisitingPendingChanges() throws Exception {
    vcs.beginChangeSet();
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");

    assertVisitorLog("createDir createFile ");
  }

  @Test
  public void testVisitingAllChanges() throws Exception {
    vcs.createFile("f", null, -1);
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.endChangeSet(null);
    vcs.beginChangeSet();
    vcs.rename("dir", "newDir");

    assertVisitorLog("rename createDir createFile ");
  }

  @Test
  public void testStop() throws Exception {
    vcs.createFile("f", null, -1);
    vcs.createDirectory("dir");

    TestVisitor visitor = new TestVisitor() {
      @Override
      public void visit(final CreateFileChange c) throws Exception {
        stop();
      }
    };
    vcs.accept(visitor);
    assertEquals("createDir ", visitor.getLog());
  }

  private void assertVisitorLog(final String expected) throws Exception {
    TestVisitor visitor = new TestVisitor();
    vcs.accept(visitor);
    assertEquals(expected, visitor.getLog());
  }

  private class TestVisitor extends ChangeVisitor {
    private String log = "";

    public void visit(CreateFileChange c) throws Exception {
      log += "createFile ";
    }

    public void visit(CreateDirectoryChange c) throws Exception {
      log += "createDir ";
    }

    public void visit(ChangeFileContentChange c) throws Exception {
    }

    public void visit(RenameChange c) throws Exception {
      log += "rename ";
    }

    public void visit(MoveChange c) throws Exception {
    }

    public void visit(DeleteChange c) throws Exception {
    }

    public String getLog() {
      return log;
    }
  }
}
