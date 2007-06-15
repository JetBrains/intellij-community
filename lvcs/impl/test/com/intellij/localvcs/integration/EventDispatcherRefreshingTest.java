package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.openapi.command.CommandEvent;
import org.junit.Ignore;
import org.junit.Test;

public class EventDispatcherRefreshingTest extends EventDispatcherTestCase {
  @Test
  public void testTreatingAllEventsDuringRefreshAsOne() {
    vcs.createDirectory("root");

    d.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));
    d.afterRefreshFinish(false);

    assertEquals(2, vcs.getRevisionsFor("root").size());
  }

  @Test
  public void testTreatingAllEventsAfterRefreshAsSeparate() {
    vcs.createDirectory("root");

    d.beforeRefreshStart(false);
    d.afterRefreshFinish(false);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    fireCreated(new TestVirtualFile("root/two", null, -1));

    assertEquals(3, vcs.getRevisionsFor("root").size());
  }

  @Test
  public void testNamingChangeSet() {
    vcs.createDirectory("root");

    d.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/f", null, -1));
    d.afterRefreshFinish(false);

    Revision r = vcs.getRevisionsFor("root").get(0);
    assertEquals("External Change", r.getCauseChangeName());
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testStartingRefreshingTwiceThrowsException() {
    d.beforeRefreshStart(false);
    try {
      d.beforeRefreshStart(false);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  @Ignore("its good idea to make it work")
  public void testFinishingRefreshingBeforeStartingItThrowsException() {
    try {
      d.afterRefreshFinish(false);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testIgnoringCommandsDuringRefresh() {
    vcs.createDirectory("root");

    d.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/one", null, -1));
    CommandEvent e = createCommandEvent();
    d.commandStarted(e);
    fireCreated(new TestVirtualFile("root/two", null, -1));
    fireCreated(new TestVirtualFile("root/three", null, -1));
    d.commandFinished(e);
    fireCreated(new TestVirtualFile("root/four", null, -1));
    d.afterRefreshFinish(false);

    assertEquals(2, vcs.getRevisionsFor("root").size());
  }
}