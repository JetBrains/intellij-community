package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestCase;
import com.intellij.localvcs.TestStorage;
import com.intellij.localvcs.integration.stubs.StubProjectRootManagerEx;
import com.intellij.localvcs.integration.stubs.StubStartupManagerEx;
import com.intellij.localvcs.integration.stubs.StubVirtualFileManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.*;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LocalVcsServiceTest extends TestCase {
  // todo can root changes and  FS changes arrive for the same directory?
  // todo for example when we create new folder and then add it to roots 
  private LocalVcs vcs;
  private LocalVcsService service;
  private List<VirtualFile> roots = new ArrayList<VirtualFile>();
  private MyStartupManager startupManager;
  private MyProjectRootManagerEx rootManager;
  private MyVirtualFileManager fileManager;
  private TestFileFilter fileFilter;

  @Before
  public void setUp() {
    initAndStartup(new LocalVcs(new TestStorage()));
  }

  private void initAndStartup(LocalVcs v) {
    initWithoutStartup(v);
    startupManager.synchronizeFileSystem();
  }

  private void initWithoutStartup(LocalVcs v) {
    vcs = v;
    startupManager = new MyStartupManager();
    rootManager = new MyProjectRootManagerEx();
    fileManager = new MyVirtualFileManager();
    fileFilter = new TestFileFilter();

    service = new LocalVcsService(vcs, startupManager, rootManager, fileManager, fileFilter);
  }

  @Test
  public void testUpdatingRootsOnStartup() {
    initWithoutStartup(new LocalVcs(new TestStorage()));

    roots.add(new TestVirtualFile("c:/root", null));
    startupManager.synchronizeFileSystem();

    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testDoesNotUpdateRootsOnBeforeStartupActivity() {
    roots.add(new TestVirtualFile("c:/root", null));
    initWithoutStartup(new LocalVcs(new TestStorage()));

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testDoesNotTrackChangesBeforeStartupActivityHasRun() {
    initWithoutStartup(new LocalVcs(new TestStorage()));

    roots.add(new TestVirtualFile("c:/root", null));
    rootManager.updateRoots();

    VirtualFile f = new TestVirtualFile("c:/root/file", null);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));

    assertFalse(vcs.hasEntry("c:/root"));
    assertFalse(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testUpdatingRootsWithContent() {
    TestVirtualFile root = new TestVirtualFile("c:/root", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);

    rootManager.updateRoots();

    assertTrue(vcs.hasEntry("c:/root"));
    assertTrue(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testUpdatingRootsWithFiltering() {
    TestVirtualFile root = new TestVirtualFile("c:/root", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);

    fileFilter.setAllFilesAllowance(false);
    rootManager.updateRoots();

    assertTrue(vcs.hasEntry("c:/root"));
    assertFalse(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testCreatingFiles() {
    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testCreatingDirectories() {
    VirtualFile f = new TestVirtualFile("dir", 345L);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));

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
    fileManager.fireFileCreated(new VirtualFileEvent(null, dir1, null, null));

    assertTrue(vcs.hasEntry("dir1"));
    assertTrue(vcs.hasEntry("dir1/dir2"));
    assertTrue(vcs.hasEntry("dir1/dir2/file"));
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile("file", b("old content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("file", "new content", 505L);
    fileManager.fireContentChanged(new VirtualFileEvent(null, f, null, null));

    Entry e = vcs.getEntry("file");
    assertEquals(c("new content"), e.getContent());
    assertEquals(505L, e.getTimestamp());
  }

  @Test
  public void testDeleting() {
    vcs.createFile("file", null, null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("file", null, null);
    fileManager.fireBeforeFileDeletion(new VirtualFileEvent(null, f, null, null));

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testRenaming() {
    vcs.createFile("old name", b("old content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("old name", null, null);
    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, f, VirtualFile.PROP_NAME, null, "new name"));

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
      fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, f, "another property", null, null));
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

    VirtualFile f = new TestVirtualFile("dir1/file", null, null);
    VirtualFile newParent = new TestVirtualFile("dir2", null);
    fileManager.fireBeforeFileMovement(new VirtualFileMoveEvent(null, f, null, newParent));

    assertFalse(vcs.hasEntry("dir1/file"));

    Entry e = vcs.findEntry("dir2/file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testFilteringFiles() {
    MyLocalVcs mockVcs = new MyLocalVcs();
    initAndStartup(mockVcs);
    fileFilter.setAllFilesAllowance(false);

    VirtualFile f = new TestVirtualFile(null, null, null);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));
    fileManager.fireContentChanged(new VirtualFileEvent(null, f, null, null));
    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, f, null, null, null));
    fileManager.fireBeforeFileMovement(new VirtualFileMoveEvent(null, f, null, null));
    fileManager.fireBeforeFileDeletion(new VirtualFileEvent(null, f, null, null));

    assertFalse(mockVcs.isAnyMethodCalled());
  }

  @Test
  public void testUnsubscribingFromFileManagerOnDisposal() {
    assertTrue(fileManager.hasListener());
    service.dispose();
    assertFalse(fileManager.hasListener());
  }

  private class MyStartupManager extends StubStartupManagerEx {
    private CacheUpdater myUpdater;

    @Override
    public FileSystemSynchronizer getFileSystemSynchronizer() {
      return new FileSystemSynchronizer() {
        @Override
        public void registerCacheUpdater(CacheUpdater u) {
          myUpdater = u;
        }
      };
    }

    public void synchronizeFileSystem() {
      myUpdater.updatingDone();
    }
  }

  private class MyProjectRootManagerEx extends StubProjectRootManagerEx {
    private ProjectFileIndex myFileIndex;
    private CacheUpdater myUpdater;

    public MyProjectRootManagerEx() {
      myFileIndex = EasyMock.createMock(ProjectFileIndex.class);
      setFileIsInContent(true);
    }

    public void setFileIsInContent(boolean result) {
      EasyMock.reset(myFileIndex);
      EasyMock.expect(myFileIndex.isInContent((VirtualFile)EasyMock.anyObject())).andStubReturn(result);
      EasyMock.replay(myFileIndex);
    }

    @Override
    @NotNull
    public VirtualFile[] getContentRoots() {
      return roots.toArray(new VirtualFile[0]);
    }

    @Override
    @NotNull
    public ProjectFileIndex getFileIndex() {
      return myFileIndex;
    }

    @Override
    public void registerChangeUpdater(CacheUpdater u) {
      myUpdater = u;
    }

    public void updateRoots() {
      if (myUpdater != null) myUpdater.updatingDone();
    }
  }

  private class MyVirtualFileManager extends StubVirtualFileManager {
    private VirtualFileListener myListener;

    public void addVirtualFileListener(@NotNull VirtualFileListener l) {
      myListener = l;
    }

    public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
      if (listener == myListener) myListener = null;
    }

    public boolean hasListener() {
      return myListener != null;
    }

    public void fireFileCreated(VirtualFileEvent e) {
      if (myListener != null) myListener.fileCreated(e);
    }

    public void fireContentChanged(VirtualFileEvent e) {
      if (myListener != null) myListener.contentsChanged(e);
    }

    public void fireBeforeFileDeletion(VirtualFileEvent e) {
      if (myListener != null) myListener.beforeFileDeletion(e);
    }

    public void fireBeforePropertyChange(VirtualFilePropertyEvent e) {
      if (myListener != null) myListener.beforePropertyChange(e);
    }

    public void fireBeforeFileMovement(VirtualFileMoveEvent e) {
      if (myListener != null) myListener.beforeFileMovement(e);
    }
  }

  private class MyLocalVcs extends LocalVcs {
    private boolean myIsAnyMethodCalled;

    public MyLocalVcs() {
      super(new TestStorage());
    }

    @Override
    public void createFile(String path, byte[] content, Long timestamp) {
      myIsAnyMethodCalled = true;
    }

    @Override
    public void createDirectory(String path, Long timestamp) {
      myIsAnyMethodCalled = true;
    }

    @Override
    public void changeFileContent(String path, byte[] content, Long timestamp) {
      myIsAnyMethodCalled = true;
    }

    @Override
    public void rename(String path, String newName) {
      myIsAnyMethodCalled = true;
    }

    @Override
    public void move(String path, String newParentPath) {
      myIsAnyMethodCalled = true;
    }

    @Override
    public void delete(String path) {
      myIsAnyMethodCalled = true;
    }

    public boolean isAnyMethodCalled() {
      return myIsAnyMethodCalled;
    }
  }
}
