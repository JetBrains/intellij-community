package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Test;

import java.util.Collections;

public class LocalVcsServiceFileListeningTest extends LocalVcsServiceTestCase {
  // todo move tests to FileListenerTest
  @Test
  public void testDoesNotTrackChangesBeforeStartup() {
    initWithoutStartup(createLocalVcs());

    VirtualFile f = new TestVirtualFile("file", null);
    fileManager.fireFileCreated(f);

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testCreatingFiles() {
    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fileManager.fireFileCreated(f);

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testTakingPhysicalFileContentOnCreation() {
    configureLocalFileSystemToReturnPhysicalContent("physical");

    VirtualFile f = new TestVirtualFile("f", "memory", null);
    fileManager.fireFileCreated(f);

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testCreatingDirectories() {
    VirtualFile f = new TestVirtualFile("dir", 345L);
    fileManager.fireFileCreated(f);

    Entry e = vcs.findEntry("dir");
    assertNotNull(e);

    assertTrue(e.isDirectory());
    assertEquals(345L, e.getTimestamp());
  }

  @Test
  public void testCreatingDirectoriesWithChildren() {
    TestVirtualFile dir1 = new TestVirtualFile("dir1", null);
    TestVirtualFile dir2 = new TestVirtualFile("dir2", null);
    TestVirtualFile file = new TestVirtualFile("file", "", null);

    dir1.addChild(dir2);
    dir2.addChild(file);
    fileManager.fireFileCreated(dir1);

    assertTrue(vcs.hasEntry("dir1"));
    assertTrue(vcs.hasEntry("dir1/dir2"));
    assertTrue(vcs.hasEntry("dir1/dir2/file"));
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile("file", b("old content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("file", "new content", 505L);
    fileManager.fireContentChanged(f);

    Entry e = vcs.getEntry("file");
    assertEquals(c("new content"), e.getContent());
    assertEquals(505L, e.getTimestamp());
  }

  @Test
  public void testTakingPhysicalFileContentOnContentChange() {
    configureLocalFileSystemToReturnPhysicalContent("physical");

    vcs.createFile("f", b("content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("f", "memory", null);
    fileManager.fireContentChanged(f);

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
  }

  @Test
  public void testRenaming() {
    vcs.createFile("old name", b("old content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("new name", null, null);
    fileManager.firePropertyChanged(f, VirtualFile.PROP_NAME, "old name");

    assertFalse(vcs.hasEntry("old name"));

    Entry e = vcs.findEntry("new name");
    assertNotNull(e);

    assertEquals(c("old content"), e.getContent());
  }

  @Test
  public void testDoNothingOnAnotherPropertyChanges() throws Exception {
    try {
      // we just shouldn't throw any exception here to meake test pass
      VirtualFile f = new TestVirtualFile(null, null, null);
      fileManager.firePropertyChanged(f, "another property", null);
    }
    catch (Exception e) {
      // test failed, lets see what's happened
      throw e;
    }
  }

  @Test
  public void testMoving() {
    vcs.createDirectory("dir1", null);
    vcs.createDirectory("dir2", null);
    vcs.createFile("dir1/file", b("content"), null);
    vcs.apply();

    TestVirtualFile oldParent = new TestVirtualFile("dir1", null);
    TestVirtualFile newParent = new TestVirtualFile("dir2", null);
    TestVirtualFile f = new TestVirtualFile("file", null, null);
    newParent.addChild(f);
    fileManager.fireFileMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("dir1/file"));

    Entry e = vcs.findEntry("dir2/file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingFromOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot", null);
    vcs.apply();

    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("anotherRoot", null);
    TestVirtualFile newParent = new TestVirtualFile("myRoot", null);

    newParent.addChild(f);
    fileFilter.setFilesNotUnderContentRoot(oldParent);

    fileManager.fireFileMoved(f, oldParent, newParent);

    Entry e = vcs.findEntry("myRoot/file");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testMovingFromOutsideOfTheContentRootsWithUnallowedType() {
    vcs.createDirectory("myRoot", null);
    vcs.apply();

    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("anotherRoot", null);
    TestVirtualFile newParent = new TestVirtualFile("myRoot", null);

    newParent.addChild(f);
    fileFilter.setFilesNotUnderContentRoot(oldParent);
    fileFilter.setNotAllowedFiles(f);

    fileManager.fireFileMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
  }

  @Test
  public void testMovingToOutsideOfTheContentRoots() {
    vcs.createDirectory("myRoot", null);
    vcs.createFile("myRoot/file", null, null);
    vcs.apply();

    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("myRoot", null);
    TestVirtualFile newParent = new TestVirtualFile("anotherRoot", null);

    newParent.addChild(f);
    fileFilter.setFilesNotUnderContentRoot(newParent);

    fileManager.fireFileMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("myRoot/file"));
    assertFalse(vcs.hasEntry("anotherRoot/file"));
  }

  @Test
  public void testMovingAroundOutsideContentRoots() {
    TestVirtualFile f = new TestVirtualFile("file", "content", null);
    TestVirtualFile oldParent = new TestVirtualFile("root1", null);
    TestVirtualFile newParent = new TestVirtualFile("root2", null);

    newParent.addChild(f);
    fileFilter.setFilesNotUnderContentRoot(oldParent, newParent);

    fileManager.fireFileMoved(f, oldParent, newParent);

    assertFalse(vcs.hasEntry("root1/file"));
    assertFalse(vcs.hasEntry("root2/file"));
  }

  @Test
  public void testFilteringFiles() {
    vcs = createMock(LocalVcs.class);
    expect(vcs.getRoots()).andReturn(Collections.<Entry>emptyList());
    vcs.apply();
    replay(vcs);

    initAndStartup(vcs);

    reset(vcs);
    expect(vcs.hasEntry((String)anyObject())).andStubReturn(false);
    replay(vcs);

    VirtualFile f = new TestVirtualFile("file", null, null);
    fileFilter.setFilesNotUnderContentRoot(f);

    fileManager.fireFileCreated(f);
    fileManager.fireContentChanged(f);
    fileManager.firePropertyChanged(f, VirtualFile.PROP_NAME, "oldName");
    fileManager.fireFileMoved(f, f, f);
    fileManager.fireFileDeletion(f);

    verify(vcs);
  }

  // todo what about refreshing order?
  // todo make integrational tests
  @Test
  public void testTreatingAllEventsDuringRefreshAsOne() {
    vcs.createDirectory("root", null);
    vcs.apply();

    fileManager.fireBeforeRefreshStart(false);
    fileManager.fireFileCreated(new TestVirtualFile("root/one", null, null));
    fileManager.fireFileCreated(new TestVirtualFile("root/two", null, null));
    fileManager.fireAfterRefreshFinish(false);

    assertEquals(2, vcs.getLabelsFor("root").size());
  }

  @Test
  public void testUnsubscribingFromFileRefreshEventsOnShutdown() {
    assertTrue(fileManager.hasVirtualFileManagerListener());
    service.shutdown();
    assertFalse(fileManager.hasVirtualFileManagerListener());
  }

  @Test
  public void testUnsubscribingFromFileManagerOnShutdown() {
    service.shutdown();

    VirtualFile f = new TestVirtualFile("file", null, null);
    fileManager.fireFileCreated(f);

    assertFalse(vcs.hasEntry("file"));
  }
}
