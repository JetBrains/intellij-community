package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import com.intellij.localvcs.integration.stubs.StubProjectRootManagerEx;
import com.intellij.localvcs.integration.stubs.StubStartupManagerEx;
import com.intellij.localvcs.integration.stubs.StubVirtualFileManagerEx;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import static org.easymock.classextension.EasyMock.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LocalVcsServiceTest extends MockedLocalFileSystemTestCase {
  // todo 1 What about roots in jars (non-local file system)?
  // todo 2 test broken storage
  // todo 3 take a look at the old-lvcs tests
  // todo 4 extract inner classes

  private LocalVcs vcs;
  private LocalVcsService service;
  private List<VirtualFile> roots = new ArrayList<VirtualFile>();
  private MyStartupManager startupManager;
  private MyProjectRootManagerEx rootManager;
  private MyVirtualFileManagerEx fileManager;
  private TestFileFilter fileFilter;

  @Before
  public void setUp() {
    initAndStartup(createLocalVcs());
  }

  private void initAndStartup(LocalVcs v) {
    initWithoutStartup(v);
    startupService();
  }

  private void startupService() {
    startupManager.synchronizeFileSystem();
  }

  private void initWithoutStartup(LocalVcs v) {
    vcs = v;
    startupManager = new MyStartupManager();
    rootManager = new MyProjectRootManagerEx();
    fileManager = new MyVirtualFileManagerEx();
    fileFilter = new TestFileFilter();

    service = new LocalVcsService(vcs, startupManager, rootManager, fileManager, fileFilter);
  }

  @Test
  public void testUpdatingRootsOnStartup() {
    initWithoutStartup(createLocalVcs());

    roots.add(new TestVirtualFile("c:/root", null));
    startupService();

    assertTrue(vcs.hasEntry("c:/root"));
  }

  private LocalVcs createLocalVcs() {
    return new LocalVcs(new TestStorage());
  }

  @Test
  public void testDoesNotUpdateRootsOnBeforeStartupActivity() {
    roots.add(new TestVirtualFile("c:/root", null));
    initWithoutStartup(createLocalVcs());

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testDoesNotTrackChangesBeforeStartup() {
    initWithoutStartup(createLocalVcs());

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

    fileFilter.setAllAreUnallowed(false);
    rootManager.updateRoots();

    assertTrue(vcs.hasEntry("c:/root"));
    assertFalse(vcs.hasEntry("c:/root/file"));
  }

  @Test
  public void testRenamingContentRoot() {
    TestVirtualFile root = new TestVirtualFile("c:/dir/rootName", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);
    rootManager.updateRoots();

    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, root, VirtualFile.PROP_NAME, null, "newName"));

    assertFalse(vcs.hasEntry("c:/dir/rootName"));
    assertTrue(vcs.hasEntry("c:/dir/newName"));
    assertTrue(vcs.hasEntry("c:/dir/newName/file"));
  }

  @Test
  public void testDeletingContentRootExternally() {
    TestVirtualFile root = new TestVirtualFile("c:/root", null);
    roots.add(root);
    rootManager.updateRoots();

    fileManager.fireBeforeFileDeletion(new VirtualFileEvent(null, root, null, null));

    assertFalse(vcs.hasEntry("c:/root"));
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
  public void testTakingPhysicalFileContentOnCreation() {
    configureLocalFileSystemToReturnPhysicalContent("physical");

    VirtualFile f = new TestVirtualFile("f", "memory", null);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
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
  public void testTakingPhysicalFileContentOnContentChange() {
    configureLocalFileSystemToReturnPhysicalContent("physical");

    vcs.createFile("f", b("content"), null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("f", "memory", null);
    fileManager.fireContentChanged(new VirtualFileEvent(null, f, null, null));

    assertEquals(c("physical"), vcs.getEntry("f").getContent());
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

    TestVirtualFile oldParent = new TestVirtualFile("dir1", null);
    TestVirtualFile newParent = new TestVirtualFile("dir2", null);
    TestVirtualFile f = new TestVirtualFile("file", null, null);
    newParent.addChild(f);
    fileManager.fireFileMoved(new VirtualFileMoveEvent(null, f, oldParent, newParent));

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

    fileManager.fireFileMoved(new VirtualFileMoveEvent(null, f, oldParent, newParent));

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
    fileFilter.setFilesWithUnallowedTypes(f);

    fileManager.fireFileMoved(new VirtualFileMoveEvent(null, f, oldParent, newParent));

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

    fileManager.fireFileMoved(new VirtualFileMoveEvent(null, f, oldParent, newParent));

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

    fileManager.fireFileMoved(new VirtualFileMoveEvent(null, f, oldParent, newParent));

    assertFalse(vcs.hasEntry("root1/file"));
    assertFalse(vcs.hasEntry("root2/file"));
  }

  @Test
  public void testFilteringFiles() {
    MyLocalVcs mockVcs = new MyLocalVcs();
    initAndStartup(mockVcs);
    fileFilter.setAllAreUnallowed(false);

    VirtualFile f = new TestVirtualFile(null, null, null);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));
    fileManager.fireContentChanged(new VirtualFileEvent(null, f, null, null));
    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, f, null, null, null));
    fileManager.fireFileMoved(new VirtualFileMoveEvent(null, f, f, f));
    fileManager.fireBeforeFileDeletion(new VirtualFileEvent(null, f, null, null));

    assertFalse(mockVcs.isAnyMethodCalled());
  }

  @Test
  public void testUnsubscribingFromFileManagerAndRootChangesOnDisposal() {
    service.shutdown();

    roots.add(new TestVirtualFile("root", null));
    rootManager.updateRoots();

    assertFalse(vcs.hasEntry("root"));

    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));

    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testRegisteringAndUnregisteringContentProvider() {
    initWithoutStartup(createLocalVcs());
    assertFalse(fileManager.hasFileContentProvider());

    startupService();
    assertTrue(fileManager.hasFileContentProvider());

    service.shutdown();
    assertFalse(fileManager.hasFileContentProvider());
  }

  @Test
  public void testContentProviderRoots() {
    roots.add(new TestVirtualFile("root", null));
    rootManager.updateRoots();

    FileContentProvider p = fileManager.getFileContentProvider();
    VirtualFile[] result = p.getCoveredDirectories();

    assertEquals(1, result.length);
    assertEquals("root", result[0].getName());
  }

  @Test
  public void testContentProviderFileListener() {
    FileContentProvider p = fileManager.getFileContentProvider();

    TestVirtualFile f = new TestVirtualFile("f", null);
    p.getVirtualFileListener().fileCreated(new VirtualFileEvent(null, f, null, null));

    assertTrue(vcs.hasEntry("f"));
  }

  //todo what if file content will be requested before startupActivity?

  @Test
  public void testContentProviding() {
    vcs.createFile("f", b("content"), null);
    vcs.apply();

    FileContentProvider p = fileManager.getFileContentProvider();
    ProvidedContent c = p.getProvidedContent(new TestVirtualFile("f", null, null));

    assertEquals(b("content").length, c.getLength());
  }

  @Test
  public void testContentForNonExistedFileIsNull() {
    FileContentProvider p = fileManager.getFileContentProvider();
    assertNull(p.getProvidedContent(new TestVirtualFile("f", null, null)));
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
      myFileIndex = createMock(ProjectFileIndex.class);
      setFileIsInContent(true);
    }

    public void setFileIsInContent(boolean result) {
      reset(myFileIndex);
      expect(myFileIndex.isInContent((VirtualFile)anyObject())).andStubReturn(result);
      replay(myFileIndex);
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


    @Override
    public void unregisterChangeUpdater(CacheUpdater u) {
      if (myUpdater == u) myUpdater = null;
    }

    public void updateRoots() {
      if (myUpdater != null) myUpdater.updatingDone();
    }
  }

  private class MyVirtualFileManagerEx extends StubVirtualFileManagerEx {
    private FileContentProvider myProvider;

    public void fireFileCreated(VirtualFileEvent e) {
      if (getListener() != null) getListener().fileCreated(e);
    }

    public void fireContentChanged(VirtualFileEvent e) {
      if (getListener() != null) getListener().contentsChanged(e);
    }

    public void fireBeforeFileDeletion(VirtualFileEvent e) {
      if (getListener() != null) getListener().beforeFileDeletion(e);
    }

    public void fireBeforePropertyChange(VirtualFilePropertyEvent e) {
      if (getListener() != null) getListener().beforePropertyChange(e);
    }

    public void fireFileMoved(VirtualFileMoveEvent e) {
      if (getListener() != null) getListener().fileMoved(e);
    }

    private VirtualFileListener getListener() {
      return myProvider == null ? null : myProvider.getVirtualFileListener();
    }

    @Override
    public void registerFileContentProvider(FileContentProvider p) {
      myProvider = p;
    }

    @Override
    public void unregisterFileContentProvider(FileContentProvider p) {
      if (myProvider == p) myProvider = null;
    }

    public boolean hasFileContentProvider() {
      return myProvider != null;
    }

    public FileContentProvider getFileContentProvider() {
      return myProvider;
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
