package com.intellij.history.integration;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.command.CommandEvent;
import org.junit.Ignore;
import org.junit.Test;

public class EventDispatcherRefreshingTest extends EventDispatcherTestCase {
  @Test
  public void testTreatingAllEventsDuringRefreshAsOne() {
    vcs.createDirectory("root");

    fireRefreshStarted();
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));
    fireRefreshFinished();

    assertEquals(2, vcs.getRevisionsFor("root").size());
  }

  @Test
  public void testTreatingAllEventsAfterRefreshAsSeparate() {
    vcs.createDirectory("root");

    fireRefreshStarted();
    fireRefreshFinished();
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));

    assertEquals(3, vcs.getRevisionsFor("root").size());
  }

  @Test
  public void testNamingChangeSet() {
    vcs.createDirectory("root");

    fireRefreshStarted();
    fireCreated(new TestVirtualFile("root/f", null, -1));
    fireRefreshFinished();

    Revision r = vcs.getRevisionsFor("root").get(0);
    assertEquals("External change", r.getCauseChangeName());
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testStartingRefreshingTwiceThrowsException() {
    fireRefreshStarted();
    try {
      fireRefreshStarted();
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testFinishingRefreshingBeforeStartingItThrowsException() {
    try {
      fireRefreshFinished();
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testIgnoringCommandsDuringRefresh() {
    vcs.createDirectory("root");

    fireRefreshStarted();
    fireCreated(new TestVirtualFile("root/one", null, -1));
    CommandEvent e = createCommandEvent();
    d.commandStarted(e);
    fireCreated(new TestVirtualFile("root/two", null, -1));
    fireCreated(new TestVirtualFile("root/three", null, -1));
    d.commandFinished(e);
    fireCreated(new TestVirtualFile("root/four", null, -1));
    fireRefreshFinished();

    assertEquals(2, vcs.getRevisionsFor("root").size());
  }
  
  @Test
  public void testAddingAndChangingFilesOnlyOnProcessing() {
    vcs.createFile("f1", cf("old"), -1);

    fireRefreshStarted();
    fireContentChanged(new TestVirtualFile("f1", "new", -1));
    fireCreated(new TestVirtualFile("f2", "", -1));
    d.afterRefreshFinish(false);

    assertEquals(c("old"), vcs.getEntry("f1").getContent());
    assertFalse(vcs.hasEntry("f2"));

    CacheUpdaterHelper.performUpdate(d);
    assertEquals(c("new"), vcs.getEntry("f1").getContent());
    assertTrue(vcs.hasEntry("f2"));
  }
  
  @Test
  public void testClearingRefreshTemporariesAfterRefreshFinishes() {
    fireRefreshStarted();
    fireCreated(new TestVirtualFile("f1", null, -1));
    fireRefreshFinished();

    fireRefreshStarted();
    fireCreated(new TestVirtualFile("f2", null, -1));
    fireRefreshFinished();  // shouldn't throw 'already exists' exception
  }

  @Test
  public void testAddingDirectoriesRightAway() {
    fireRefreshStarted();
    fireCreated(new TestVirtualFile("dir"));

    assertTrue(vcs.hasEntry("dir"));
  }

  private void fireRefreshStarted() {
    d.beforeRefreshStart(false);
  }

  private void fireRefreshFinished() {
    d.afterRefreshFinish(false);
    CacheUpdaterHelper.performUpdate(d);
  }
}