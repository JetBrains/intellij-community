package com.intellij.history.integration;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.Clock;
import com.intellij.openapi.command.CommandEvent;
import org.junit.Test;

import java.util.List;

public class EventDispatcherActionPerformingTest extends EventDispatcherTestCase {
  @Test
  public void testRegisteringUnsavedDocumentsBeforeEnteringState() {
    vcs.createFile("file", cf("old"), 123L, false);

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
    long timestamp = -1;
    vcs.createFile("dir/one", null, timestamp, false);
    long timestamp1 = -1;
    vcs.createFile("dir/two", null, timestamp1, false);
    vcs.endChangeSet(null);

    gateway.addUnsavedDocument("dir/one", "one");
    gateway.addUnsavedDocument("dir/two", "two");
    d.startAction();

    assertEquals(2, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsBeforeEnteringSeparately() {
    long timestamp = -1;
    vcs.createFile("f", cf("one"), timestamp, false);

    gateway.addUnsavedDocument("f", "two");
    d.startAction();
    vcs.changeFileContent("f", cf("three"), -1);
    d.finishAction(null);

    assertEquals(3, vcs.getRevisionsFor("f").size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsBeforeExitingState() {
    vcs.createFile("file", cf("old"), 123L, false);
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
    long timestamp2 = -1;
    vcs.createFile("dir/one", null, timestamp2, false);
    long timestamp = -1;
    vcs.createFile("dir/two", null, timestamp, false);
    vcs.endChangeSet(null);

    d.startAction();
    long timestamp1 = -1;
    vcs.createFile("dir/three", null, timestamp1, false);

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
    long timestamp = -1;
    vcs.createFile("f", cf("1"), timestamp, false);

    CommandEvent e = createCommandEvent("command");
    d.commandStarted(e);
    vcs.changeFileContent("f", cf("2"), -1);
    gateway.addUnsavedDocument("f", "3");

    d.startAction();
    vcs.changeFileContent("f", cf("4"), -1);
    gateway.addUnsavedDocument("f", "5");
    d.finishAction("action");

    vcs.changeFileContent("f", cf("6"), -1);
    d.commandFinished(e);

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
