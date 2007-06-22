package com.intellij.history.integration;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.project.Project;
import static org.easymock.classextension.EasyMock.createMock;
import org.junit.Ignore;
import org.junit.Test;

public class EventDispatcherCommandProcessingTest extends EventDispatcherTestCase {
  @Test
  public void testTreatingAllEventsAsOne() {
    CommandEvent e = createCommandEvent();

    d.commandStarted(e);
    fireCreated(new TestVirtualFile("file", null, -1));
    fireContentChanged(new TestVirtualFile("file", null, -1));
    d.commandFinished(e);

    assertEquals(1, vcs.getRevisionsFor("file").size());
  }

  @Test
  public void testNamedCommands() {
    CommandEvent e = createCommandEvent("name");

    d.commandStarted(e);
    fireCreated(new TestVirtualFile("file", null, -1));
    d.commandFinished(e);

    assertEquals("name", vcs.getRevisionsFor("file").get(0).getCauseChangeName());
  }

  @Test
  public void testIgnoringCommandsForAnotherProject() {
    Project anotherProject = createMock(Project.class);

    CommandEvent e = createCommandEvent("command", anotherProject);
    d.commandStarted(e);
    fireCreated(new TestVirtualFile("file", null, -1));
    d.commandFinished(e);

    assertNull(vcs.getRevisionsFor("file").get(0).getCauseChangeName());
  }

  @Test
  public void testDeletionAndRecreationOfFile() {
    CommandEvent e = createCommandEvent();

    d.commandStarted(e);
    TestVirtualFile f = new TestVirtualFile("f", "a", -1);
    fireCreated(f);
    fireDeletion(f);
    fireCreated(new TestVirtualFile("f", "b", -1));
    d.commandFinished(e);

    assertTrue(vcs.hasEntry("f"));
    assertEquals(c("b"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testTreatingAllEventsAfterCommandAsSeparate() {
    vcs.createDirectory("root");

    CommandEvent e = createCommandEvent();
    d.commandStarted(e);
    d.commandFinished(e);

    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));

    assertEquals(3, vcs.getRevisionsFor("root").size());
  }

  @Test
  public void testIgnoringRefreshesDuringCommandProcessing() {
    vcs.createDirectory("root");

    CommandEvent e = createCommandEvent();

    d.commandStarted(e);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    d.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/two", null, -1));
    fireCreated(new TestVirtualFile("root/three", null, -1));
    d.afterRefreshFinish(false);
    fireCreated(new TestVirtualFile("root/four", null, -1));
    d.commandFinished(e);

    assertEquals(2, vcs.getRevisionsFor("root").size());
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testTryingToStartCommandProcessingTwiceThrowsException() {
    CommandEvent e = createCommandEvent();

    d.commandStarted(e);
    try {
      d.commandStarted(e);
      fail();
    }
    catch (IllegalStateException ex) {
    }
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testFinishingCommandProcessingBeforeStartingItThrowsException() {
    try {
      d.commandFinished(createCommandEvent());
      fail();
    }
    catch (IllegalStateException e) {
    }
  }
}