package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import org.junit.Before;
import org.junit.Test;

public class FileListenerTest extends FileListenerTestCase {

  @Before
  public void setUp() {
    l = new FileListener(vcs, fileSystem, new TestFileFilter());
  }

  @Test
  public void testCreationOfDirectoryWithChildrenAreThreatedAsOneChange() {
    TestVirtualFile dir = new TestVirtualFile("dir", null);
    dir.addChild(new TestVirtualFile("one", null, null));
    dir.addChild(new TestVirtualFile("two", null, null));
    fireCreation(dir);

    assertTrue(vcs.hasEntry("dir"));
    assertTrue(vcs.hasEntry("dir/one"));
    assertTrue(vcs.hasEntry("dir/two"));

    assertEquals(1, vcs.getLabelsFor("dir").size());
  }

  @Test
  public void testTreatingAllEventsDuringRefreshAsOne() {
    vcs.createDirectory("root", null);
    vcs.apply();

    l.beforeRefreshStart(false);
    fireCreation(new TestVirtualFile("root/one", null, null));
    fireCreation(new TestVirtualFile("root/two", null, null));
    l.afterRefreshFinish(false);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testTreatingAllEventsAfterRefreshAsSeparate() {
    vcs.createDirectory("root", null);
    vcs.apply();

    l.beforeRefreshStart(false);
    l.afterRefreshFinish(false);
    fireCreation(new TestVirtualFile("root/one", null, null));
    fireCreation(new TestVirtualFile("root/two", null, null));

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

  @Test
  public void testDeletionFromDirectory() {
    vcs.createDirectory("dir", null);
    vcs.createFile("file", null, null);
    vcs.apply();

    VirtualFile dir = new TestVirtualFile("dir", null, null);
    VirtualFile f = new TestVirtualFile("file", null, null);
    fireDeletion(f, dir);

    assertTrue(vcs.hasEntry("dir"));
    assertFalse(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testDeletionWithoutParent() {
    vcs.createFile("file", null, null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("file", null, null);
    fireDeletion(f, null);

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testDeletionOfFileThanIsNotUnderVcsDoesNotThrowException() {
    VirtualFile f = new TestVirtualFile("non-existent", null, null);
    fireDeletion(f, null); // should'n throw
  }
}
