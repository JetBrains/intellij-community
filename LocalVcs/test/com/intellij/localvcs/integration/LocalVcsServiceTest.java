package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import com.intellij.localvcs.TestCase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
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
  private MyProjectRootManager rootManager;
  private MyVirtualFileManager fileManager;

  @Before
  public void setUp() {
    initAndStartup(new LocalVcs(new TestStorage()));
  }

  private void initAndStartup(LocalVcs v) {
    initWithoutStartup(v);
    startupManager.synchronizeFileSystem();
  }

  private void initWithoutStartup(final LocalVcs v) {
    vcs = v;
    startupManager = new MyStartupManager();
    rootManager = new MyProjectRootManager();
    fileManager = new MyVirtualFileManager();

    service = new LocalVcsService(vcs, startupManager, rootManager, fileManager);
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
  public void testUpdatingRoots() {
    roots.add(new TestVirtualFile("c:/root", null));
    rootManager.updateRoots();

    assertTrue(vcs.hasEntry("c:/root"));
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
    VirtualFile f = new TestVirtualFile("file", 345L);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertTrue(e.isDirectory());
    assertEquals(345L, e.getTimestamp());
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
      // test failed, lets just see what happened
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
  public void testSkippingEventsFromAnotherProject() {
    boolean isAnyMethodCalled[] = {false};
    initAndStartup(new MyLocalVcs(isAnyMethodCalled));
    rootManager.setModuleForFileToNull();

    VirtualFile f = new TestVirtualFile(null, null, null);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));
    fileManager.fireContentChanged(new VirtualFileEvent(null, f, null, null));
    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, f, null, null, null));
    fileManager.fireBeforeFileMovement(new VirtualFileMoveEvent(null, f, null, null));
    fileManager.fireBeforeFileDeletion(new VirtualFileEvent(null, f, null, null));

    assertFalse(isAnyMethodCalled[0]);
  }

  @Test
  public void testUnsubscribingFromFileManagerOnDisposal() {
    assertTrue(fileManager.hasListener());
    service.dispose();
    assertFalse(fileManager.hasListener());
  }

  private class MyStartupManager extends StartupManager {
    private CacheUpdater myUpdater;

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

    public void registerStartupActivity(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    public void registerPostStartupActivity(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    public void runPostStartup(Runnable runnable) {
      throw new UnsupportedOperationException();
    }

    public void runWhenProjectIsInitialized(Runnable runnable) {
      throw new UnsupportedOperationException();
    }
  }

  private class MyProjectRootManager extends ProjectRootManagerEx {
    private ProjectFileIndex myFileIndex;
    private CacheUpdater myUpdater;

    public MyProjectRootManager() {
      myFileIndex = EasyMock.createMock(ProjectFileIndex.class);
      setModuleForFile(EasyMock.createMock(Module.class));
    }

    private void setModuleForFile(Module m) {
      EasyMock.reset(myFileIndex);
      EasyMock.expect(myFileIndex.getModuleForFile((VirtualFile)EasyMock.anyObject())).andStubReturn(m);
      EasyMock.replay(myFileIndex);
    }

    @NotNull
    public VirtualFile[] getContentRoots() {
      return roots.toArray(new VirtualFile[0]);
    }

    @NotNull
    public ProjectFileIndex getFileIndex() {
      return myFileIndex;
    }

    public void setModuleForFileToNull() {
      setModuleForFile(null);
    }

    public void registerChangeUpdater(CacheUpdater u) {
      myUpdater = u;
    }

    public void clearScopesCachesForModules() {
      // empty
    }

    public void updateRoots() {
      if (myUpdater != null) myUpdater.updatingDone();
    }

    public FileSystemSynchronizer getFileSystemSynchronizer() {
      throw new UnsupportedOperationException();
    }

    public void addModuleRootListener(ModuleRootListener listener) {
      throw new UnsupportedOperationException();
    }

    public void addModuleRootListener(ModuleRootListener listener, Disposable parentDisposable) {
      throw new UnsupportedOperationException();
    }

    public void removeModuleRootListener(ModuleRootListener listener) {
      throw new UnsupportedOperationException();
    }

    public VirtualFile[] getRootFiles(ProjectRootType type) {
      throw new UnsupportedOperationException();
    }

    public VirtualFile[] getContentSourceRoots() {
      throw new UnsupportedOperationException();
    }

    public String getCompilerOutputUrl() {
      throw new UnsupportedOperationException();
    }

    public VirtualFile getCompilerOutput() {
      throw new UnsupportedOperationException();
    }

    public void setCompilerOutputUrl(String compilerOutputUrl) {
      throw new UnsupportedOperationException();
    }

    public VirtualFile[] getFullClassPath() {
      throw new UnsupportedOperationException();
    }

    public ProjectJdk getJdk() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    public ProjectJdk getProjectJdk() {
      throw new UnsupportedOperationException();
    }

    public String getProjectJdkName() {
      throw new UnsupportedOperationException();
    }

    public void setProjectJdk(@Nullable ProjectJdk jdk) {
      throw new UnsupportedOperationException();
    }

    public void setProjectJdkName(String name) {
      throw new UnsupportedOperationException();
    }

    public void multiCommit(ModifiableRootModel[] rootModels) {
      throw new UnsupportedOperationException();
    }

    public void multiCommit(ModifiableModuleModel moduleModel, ModifiableRootModel[] rootModels) {
      throw new UnsupportedOperationException();
    }

    public void checkCircularDependency(ModifiableRootModel[] rootModels, ModifiableModuleModel moduleModel)
      throws ModuleCircularDependencyException {
      throw new UnsupportedOperationException();
    }

    public long getModificationCount() {
      throw new UnsupportedOperationException();
    }

    public void setLanguageLevel(LanguageLevel level) {
      throw new UnsupportedOperationException();
    }

    public LanguageLevel getLanguageLevel() {
      throw new UnsupportedOperationException();
    }

    public void unregisterChangeUpdater(CacheUpdater updater) {
      throw new UnsupportedOperationException();
    }

    public void addProjectJdkListener(ProjectJdkListener listener) {
      throw new UnsupportedOperationException();
    }

    public void removeProjectJdkListener(ProjectJdkListener listener) {
      throw new UnsupportedOperationException();
    }

    public void beforeRootsChange(boolean filetypes) {
      throw new UnsupportedOperationException();
    }

    public void rootsChanged(boolean filetypes) {
      throw new UnsupportedOperationException();
    }

    public GlobalSearchScope getScopeForLibraryUsedIn(List<Module> modulesLibraryIsUsedIn) {
      throw new UnsupportedOperationException();
    }

    public GlobalSearchScope getScopeForJdk(final JdkOrderEntry jdkOrderEntry) {
      throw new UnsupportedOperationException();
    }
  }

  private class MyVirtualFileManager extends VirtualFileManager {
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

    public VirtualFileSystem[] getFileSystems() {
      throw new UnsupportedOperationException();
    }

    public VirtualFileSystem getFileSystem(String protocol) {
      throw new UnsupportedOperationException();
    }

    public void refresh(boolean asynchronous) {
      throw new UnsupportedOperationException();
    }

    public void refresh(boolean asynchronous, @Nullable Runnable postAction) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    public VirtualFile findFileByUrl(@NonNls @NotNull String url) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
      throw new UnsupportedOperationException();
    }

    public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
      throw new UnsupportedOperationException();
    }

    public void dispatchPendingEvent(@NotNull VirtualFileListener listener) {
      throw new UnsupportedOperationException();
    }

    public void addModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
      throw new UnsupportedOperationException();
    }

    public void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
      throw new UnsupportedOperationException();
    }

    public void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files) {
      throw new UnsupportedOperationException();
    }

    public void addVirtualFileManagerListener(VirtualFileManagerListener listener) {
      throw new UnsupportedOperationException();
    }

    public void removeVirtualFileManagerListener(VirtualFileManagerListener listener) {
      throw new UnsupportedOperationException();
    }
  }

  private class MyLocalVcs extends LocalVcs {
    private boolean[] myIsAnyMethodCalled;

    public MyLocalVcs(boolean[] isAnyMethodCalled) {
      super(new TestStorage());
      myIsAnyMethodCalled = isAnyMethodCalled;
    }

    @Override
    public void createFile(String path, byte[] content, Long timestamp) {
      myIsAnyMethodCalled[0] = true;
    }

    @Override
    public void createDirectory(String path, Long timestamp) {
      myIsAnyMethodCalled[0] = true;
    }

    @Override
    public void changeFileContent(String path, byte[] content, Long timestamp) {
      myIsAnyMethodCalled[0] = true;
    }

    @Override
    public void rename(String path, String newName) {
      myIsAnyMethodCalled[0] = true;
    }

    @Override
    public void move(String path, String newParentPath) {
      myIsAnyMethodCalled[0] = true;
    }

    @Override
    public void delete(String path) {
      myIsAnyMethodCalled[0] = true;
    }
  }
}
