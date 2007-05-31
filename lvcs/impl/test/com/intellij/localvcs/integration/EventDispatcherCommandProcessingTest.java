package com.intellij.localvcs.integration;

import org.junit.Ignore;
import org.junit.Test;

public class EventDispatcherCommandProcessingTest extends EventDispatcherTestCase {
  @Test
  public void testTreatingAllEventsAsOne() {
    d.commandStarted(null);
    fireCreated(new TestVirtualFile("file", null, -1));
    fireContentChanged(new TestVirtualFile("file", null, -1));
    d.commandFinished(createCommandEvent(null));

    assertEquals(1, vcs.getRevisionsFor("file").size());
  }

  @Test
  public void testNamedCommands() {
    d.commandStarted(null);
    fireCreated(new TestVirtualFile("file", null, -1));
    d.commandFinished(createCommandEvent("name"));

    assertEquals("name", vcs.getRevisionsFor("file").get(0).getCauseChangeName());
  }

  @Test
  public void testDeletionAndRecreationOfFile() {
    d.commandStarted(null);
    TestVirtualFile f = new TestVirtualFile("f", "a", -1);
    fireCreated(f);
    fireDeleted(f, null);
    fireCreated(new TestVirtualFile("f", "b", -1));
    d.commandFinished(createCommandEvent(null));

    assertTrue(vcs.hasEntry("f"));
    assertEquals(c("b"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testTreatingAllEventsAfterCommandAsSeparate() {
    vcs.createDirectory("root");

    d.commandStarted(null);
    d.commandFinished(createCommandEvent(null));
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));

    assertEquals(3, vcs.getRevisionsFor("root").size());
  }

  @Test
  public void testIgnoringRefreshesDuringCommandProcessing() {
    vcs.createDirectory("root");

    d.commandStarted(null);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    d.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/two", null, -1));
    fireCreated(new TestVirtualFile("root/three", null, -1));
    d.afterRefreshFinish(false);
    fireCreated(new TestVirtualFile("root/four", null, -1));
    d.commandFinished(createCommandEvent(null));

    assertEquals(2, vcs.getRevisionsFor("root").size());
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testTryingToStartCommandProcessingTwiceThrowsException() {
    d.commandStarted(null);
    try {
      d.commandStarted(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testFinishingCommandProcessingBeforeStartingItThrowsException() {
    try {
      d.commandFinished(createCommandEvent(null));
      fail();
    }
    catch (IllegalStateException e) {
    }
  }
}