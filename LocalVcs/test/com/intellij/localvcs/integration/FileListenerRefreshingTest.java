package com.intellij.localvcs.integration;

import org.junit.Test;

public class FileListenerRefreshingTest extends FileListenerTestCase {
  @Test
  public void testTreatingAllEventsDuringRefreshAsOne() {
    vcs.createDirectory("root");

    l.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));
    l.afterRefreshFinish(false);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testTreatingAllEventsAfterRefreshAsSeparate() {
    vcs.createDirectory("root");

    l.beforeRefreshStart(false);
    l.afterRefreshFinish(false);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));

    assertEquals(3, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testStartingRefreshingTwiceThrowsException() {
    l.beforeRefreshStart(false);
    try {
      l.beforeRefreshStart(false);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testFinishingRefreshingBeforeStartingItThrowsException() {
    try {
      l.afterRefreshFinish(false);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testProcessingCommandDuringRefreshThrowsException() {
    l.beforeRefreshStart(false);
    try {
      l.commandStarted(createCommandEvent(null));
      fail();
    }
    catch (IllegalStateException e) {
    }
    try {
      l.commandFinished(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }
}