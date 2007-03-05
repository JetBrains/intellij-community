package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import org.junit.Test;

public class FileListenerRefreshingTest extends FileListenerTestCase {
  @Test
  public void testTreatingAllEventsDuringRefreshAsOne() {
    vcs.createDirectory("root", null);
    vcs.apply();

    l.beforeRefreshStart(false);
    fireCreated(new TestVirtualFile("root/one", null, null));
    fireCreated(new TestVirtualFile("root/two", null, null));
    l.afterRefreshFinish(false);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testTreatingAllEventsAfterRefreshAsSeparate() {
    vcs.createDirectory("root", null);
    vcs.apply();

    l.beforeRefreshStart(false);
    l.afterRefreshFinish(false);
    fireCreated(new TestVirtualFile("root/one", null, null));
    fireCreated(new TestVirtualFile("root/two", null, null));

    assertEquals(3, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testIsFileContentChangedByRefresh() {
    vcs.createFile("f", null, null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("f", null, null);

    l.beforeRefreshStart(false);
    assertFalse(l.isFileContentChangedByRefresh(f));

    l.contentsChanged(new VirtualFileEvent(null, f, null, null));
    assertTrue(l.isFileContentChangedByRefresh(f));

    l.afterRefreshFinish(false);
    assertFalse(l.isFileContentChangedByRefresh(f));
  }

  @Test
  public void testIsFileContentChangedByRefreshOutsideOfRefresh() {
    vcs.createFile("f", null, null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("f", null, null);
    assertFalse(l.isFileContentChangedByRefresh(f));

    l.contentsChanged(new VirtualFileEvent(null, f, null, null));
    assertFalse(l.isFileContentChangedByRefresh(f));

    l.beforeRefreshStart(false);
    assertFalse(l.isFileContentChangedByRefresh(f));
  }
}