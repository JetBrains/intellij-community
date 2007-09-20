package com.intellij.history.integration;

import com.intellij.history.core.revisions.Revision;
import com.intellij.openapi.command.CommandEvent;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

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
    long timestamp = -1;
    vcs.createFile("f1", cf("old"), timestamp, false);

    fireRefreshStarted();
    fireContentChanged(testFile("f1", "new"));
    fireCreated(testFile("f2", ""));
    d.afterRefreshFinish(false);

    assertEquals(c("old"), vcs.getEntry("f1").getContent());
    assertFalse(vcs.hasEntry("f2"));

    CacheUpdaterHelper.performUpdate(d);
    assertEquals(c("new"), vcs.getEntry("f1").getContent());
    assertTrue(vcs.hasEntry("f2"));
  }

  @Test
  public void testAddingDirectoriesAntItsChildDirsRightAway() {
    fireRefreshStarted();

    TestVirtualFile dir = new TestVirtualFile("dir");
    dir.addChild(new TestVirtualFile("subDir"));
    fireCreated(dir);

    assertTrue(vcs.hasEntry("dir"));
    assertTrue(vcs.hasEntry("dir/subDir"));
  }

  @Test
  public void testAddingDirectoryChildFilesOnlyOnProcessing() {
    fireRefreshStarted();

    TestVirtualFile dir = new TestVirtualFile("dir");
    dir.addChild(new TestVirtualFile("f", null, -1));
    fireCreated(dir);

    assertTrue(vcs.hasEntry("dir"));
    assertFalse(vcs.hasEntry("dir/f"));

    d.afterRefreshFinish(false);

    CacheUpdaterHelper.performUpdate(d);
    assertTrue(vcs.hasEntry("dir/f"));
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
  public void testSeveralUpdatesInsideOneRefresh() {
    vcs.createDirectory("dir");
    d.beforeRefreshStart(false);

    fireCreated(new TestVirtualFile("dir/f1", null, -1));
    assertFalse(vcs.hasEntry("dir/f1"));

    CacheUpdaterHelper.performUpdate(d);
    assertTrue(vcs.hasEntry("dir/f1"));

    fireCreated(new TestVirtualFile("dir/f2", null, -1));
    assertFalse(vcs.hasEntry("dir/f2"));

    CacheUpdaterHelper.performUpdate(d);
    assertTrue(vcs.hasEntry("dir/f2"));

    d.afterRefreshFinish(false);

    List<Revision> rr = vcs.getRevisionsFor("dir");
    assertEquals(2, rr.size());
    assertEquals("External change", rr.get(0).getCauseChangeName());
  }
  
  @Test
  public void testChangeSetsDuringSeveralUpdatersInsideOneRefresh() {
    vcs.createDirectory("dir");
    long timestamp = -1;
    vcs.createFile("dir/f", null, timestamp, false);

    assertEquals(2, vcs.getRevisionsFor("dir").size());

    d.beforeRefreshStart(false);

    vcs.createDirectory("dir/dir1");
    long timestamp3 = -1;
    vcs.createFile("dir/f1", null, timestamp3, false);

    CacheUpdaterHelper.performUpdate(d);

    vcs.createDirectory("dir/dir2");
    long timestamp1 = -1;
    vcs.createFile("dir/f2", null, timestamp1, false);

    CacheUpdaterHelper.performUpdate(d);

    d.afterRefreshFinish(false);

    assertEquals(3, vcs.getRevisionsFor("dir").size());

    long timestamp2 = -1;
    vcs.createFile("dir/f3", null, timestamp2, false);
    assertEquals(4, vcs.getRevisionsFor("dir").size());
  }

  @Test
  public void testEmptyRefresh() {
    d.beforeRefreshStart(false);
    CacheUpdaterHelper.performUpdate(d);
    d.afterRefreshFinish(false);
  }

  @Test
  public void testEmptyRefreshWithSeveralUpdates() {
    d.beforeRefreshStart(false);
    CacheUpdaterHelper.performUpdate(d);
    CacheUpdaterHelper.performUpdate(d);
    d.afterRefreshFinish(false);
  }

  private void fireRefreshStarted() {
    d.beforeRefreshStart(false);
  }

  private void fireRefreshFinished() {
    CacheUpdaterHelper.performUpdate(d);
    d.afterRefreshFinish(false);
  }
}