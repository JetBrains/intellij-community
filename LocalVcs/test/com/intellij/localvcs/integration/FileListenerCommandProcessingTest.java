package com.intellij.localvcs.integration;

import org.junit.Ignore;
import org.junit.Test;

public class FileListenerCommandProcessingTest extends FileListenerTestCase {
  @Test
  @Ignore
  public void testTreatingAllEventsAsOne() {
    vcs.createDirectory("root", null);
    vcs.apply();

    l.commandStarted(null);
    fireCreated(new TestVirtualFile("root/one", null, null));
    fireCreated(new TestVirtualFile("root/two", null, null));
    l.commandFinished(null);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }

  @Test
  @Ignore
  public void testDeletionAndRecreationOfFile() {
    l.commandStarted(null);
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
    vcs.apply();

    l.commandStarted(null);
    l.commandFinished(null);
    fireCreated(new TestVirtualFile("root/one", null, null));
    fireCreated(new TestVirtualFile("root/two", null, null));

    assertEquals(3, vcs.getLabelsFor("root").size());
  }

  @Test
  @Ignore
  public void testIgnoringRefreshesDuringCommandProcessing() {
    vcs.createDirectory("root", null);
    vcs.apply();

    l.commandStarted(null);
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
  @Ignore
  public void testTryingToStartCommandProcessingTwiceThrowsException() {
    l.commandStarted(null);
    try {
      l.commandStarted(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  @Ignore
  public void testFinishingCommandProcessingBeforeStartingItThrowsException() {
    try {
      l.commandFinished(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }
}