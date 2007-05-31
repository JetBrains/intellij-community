package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.revisions.Revision;
import org.junit.Test;

import java.util.List;

public class EventDispatcherActionPerformingTest extends EventDispatcherTestCase {
  @Test
  public void testRegisteringUnsavedDocumentsBeforeEnteringState() {
    vcs.createFile("file", cf("old"), 123L);

    Clock.setCurrentTimestamp(456);
    gateway.addUnsavedDocument("file", "new");

    d.startAction();

    assertEquals(c("new"), vcs.getEntry("file").getContent());
    assertEquals(456L, vcs.getEntry("file").getTimestamp());

    assertEquals(2, vcs.getRevisionsFor("file").size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsAsOneChangeSetBeforeEntering() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.createFile("dir/one", null, -1);
    vcs.createFile("dir/two", null, -1);
    vcs.endChangeSet(null);

    gateway.addUnsavedDocument("dir/one", "one");
    gateway.addUnsavedDocument("dir/two", "two");
    d.startAction();

    assertEquals(2, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsBeforeEnteringSeparately() {
    vcs.createFile("f", cf("one"), -1);

    gateway.addUnsavedDocument("f", "two");
    d.startAction();
    vcs.changeFileContent("f", cf("three"), -1);
    d.finishAction(null);

    assertEquals(3, vcs.getRevisionsFor("f").size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsBeforeExitingState() {
    vcs.createFile("file", cf("old"), 123L);
    d.startAction();

    Clock.setCurrentTimestamp(789);
    gateway.addUnsavedDocument("file", "new");

    d.finishAction(null);

    assertEquals(c("new"), vcs.getEntry("file").getContent());
    assertEquals(789L, vcs.getEntry("file").getTimestamp());

    assertEquals(2, vcs.getRevisionsFor("file").size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsBeforeExitingStateWithinInnerChangeset() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.createFile("dir/one", null, -1);
    vcs.createFile("dir/two", null, -1);
    vcs.endChangeSet(null);

    d.startAction();
    vcs.createFile("dir/three", null, -1);

    gateway.addUnsavedDocument("dir/one", "one");
    gateway.addUnsavedDocument("dir/two", "two");
    d.finishAction(null);

    assertEquals(2, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testPuttingLabel() {
    d.startAction();
    vcs.createDirectory("dir");
    d.finishAction("label");

    assertEquals("label", vcs.getRevisionsFor("dir").get(0).getCauseChangeName());
  }

  @Test
  public void testActionInsideCommand() {
    vcs.createFile("f", cf("1"), -1);

    d.commandStarted(null);
    vcs.changeFileContent("f", cf("2"), -1);
    gateway.addUnsavedDocument("f", "3");

    d.startAction();
    vcs.changeFileContent("f", cf("4"), -1);
    gateway.addUnsavedDocument("f", "5");
    d.finishAction("action");

    vcs.changeFileContent("f", cf("6"), -1);
    d.commandFinished(createCommandEvent("command"));

    List<Revision> rr = vcs.getRevisionsFor("f");
    assertEquals(3, rr.size());

    assertEquals(c("6"), rr.get(0).getEntry().getContent());
    assertEquals(c("3"), rr.get(1).getEntry().getContent());
    assertEquals(c("1"), rr.get(2).getEntry().getContent());

    assertEquals("command", rr.get(0).getCauseChangeName());
    assertNull(rr.get(1).getCauseChangeName());
    assertNull(rr.get(2).getCauseChangeName());
  }
}
