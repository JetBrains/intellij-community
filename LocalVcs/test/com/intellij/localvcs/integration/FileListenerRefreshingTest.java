package com.intellij.localvcs.integration;

import org.junit.Ignore;
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
  @Ignore("its good idea to make it work")
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
  public void testIgnoringCommandsDuringRefresh() {
    vcs.createDirectory("root");

    l.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    l.commandStarted(createCommandEvent(null));
    fireCreated(new TestVirtualFile("root/two", null, -1));
    fireCreated(new TestVirtualFile("root/three", null, -1));
    l.commandFinished(null);
    fireCreated(new TestVirtualFile("root/four", null, -1));
    l.afterRefreshFinish(false);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }
}