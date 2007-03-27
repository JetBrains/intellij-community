package com.intellij.localvcs.integration;

import org.junit.Ignore;
import org.junit.Test;

public class FileListenerCommandProcessingTest extends FileListenerTestCase {
  @Test
  public void testTreatingAllEventsAsOne() {
    l.commandStarted(createCommandEvent(null));
    fireCreated(new TestVirtualFile("file", null, -1));
    fireContentChanged(new TestVirtualFile("file", null, -1));
    l.commandFinished(null);

    assertEquals(1, vcs.getLabelsFor("file").size());
  }

  @Test
  public void testLabeling() {
    l.commandStarted(createCommandEvent("label"));
    fireCreated(new TestVirtualFile("file", null, -1));
    l.commandFinished(null);

    assertEquals("label", vcs.getLabelsFor("file").get(0).getName());
  }

  @Test
  public void testDeletionAndRecreationOfFile() {
    l.commandStarted(createCommandEvent(null));
    TestVirtualFile f = new TestVirtualFile("f", "a", -1);
    fireCreated(f);
    fireDeleted(f, null);
    fireCreated(new TestVirtualFile("f", "b", -1));
    l.commandFinished(null);

    assertTrue(vcs.hasEntry("f"));
    assertEquals(c("b"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testTreatingAllEventsAfterCommandAsSeparate() {
    vcs.createDirectory("root");

    l.commandStarted(createCommandEvent(null));
    l.commandFinished(null);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));

    assertEquals(3, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testIgnoringRefreshesDuringCommandProcessing() {
    vcs.createDirectory("root");

    l.commandStarted(createCommandEvent(null));
    fireCreated(new TestVirtualFile("root/one", null, -1));
    l.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/two", null, -1));
    fireCreated(new TestVirtualFile("root/three", null, -1));
    l.afterRefreshFinish(false);
    fireCreated(new TestVirtualFile("root/four", null, -1));
    l.commandFinished(null);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testTryingToStartCommandProcessingTwiceThrowsException() {
    l.commandStarted(createCommandEvent(null));
    try {
      l.commandStarted(createCommandEvent(null));
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testFinishingCommandProcessingBeforeStartingItThrowsException() {
    try {
      l.commandFinished(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }
}