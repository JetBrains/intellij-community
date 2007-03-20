package com.intellij.localvcs.integration;

import org.junit.Test;

public class FileListenerCommandProcessingTest extends FileListenerTestCase {
  @Test
  public void testTreatingAllEventsAsOne() {
    l.commandStarted(createCommandEvent(null));
    fireCreated(new TestVirtualFile("file", null, null));
    fireContentChanged(new TestVirtualFile("file", null, null));
    l.commandFinished(null);

    assertEquals(1, vcs.getLabelsFor("file").size());
  }

  @Test
  public void testLabeling() {
    l.commandStarted(createCommandEvent("label"));
    fireCreated(new TestVirtualFile("file", null, null));
    l.commandFinished(null);

    assertEquals("label", vcs.getLabelsFor("file").get(0).getName());
  }

  @Test
  public void testDeletionAndRecreationOfFile() {
    l.commandStarted(createCommandEvent(null));
    TestVirtualFile f = new TestVirtualFile("f", "a", null);
    fireCreated(f);
    fireDeleted(f, null);
    fireCreated(new TestVirtualFile("f", "b", null));
    l.commandFinished(null);

    assertTrue(vcs.hasEntry("f"));
    assertEquals(c("b"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testTreatingAllEventsAfterCommandAsSeparate() {
    vcs.createDirectory("root", null);

    l.commandStarted(createCommandEvent(null));
    l.commandFinished(null);
    fireCreated(new TestVirtualFile("root/one", null, null));
    fireCreated(new TestVirtualFile("root/two", null, null));

    assertEquals(3, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testIgnoringRefreshesDuringCommandProcessing() {
    vcs.createDirectory("root", null);

    l.commandStarted(createCommandEvent(null));
    fireCreated(new TestVirtualFile("root/one", null, null));
    l.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/two", null, null));
    fireCreated(new TestVirtualFile("root/three", null, null));
    l.afterRefreshFinish(false);
    fireCreated(new TestVirtualFile("root/four", null, null));
    l.commandFinished(null);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }

  @Test
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