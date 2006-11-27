package com.intellij.localvcs.integration;

import com.intellij.ProjectTopics;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Messages;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import java.util.ArrayList;
import java.util.List;

public class LocalVcsServiceTest extends Assert {
  private LocalVcs vcs;
  private MessageBus bus;
  private LocalVcsService service;
  private List<VirtualFile> roots = new ArrayList<VirtualFile>();
  private MyStartupManager startupManager;
  private MyProjectRootManager rootManager;
  private MyVirtualFileManager fileManager;

  @Before
  public void setUp() {
    init(new LocalVcs(new TestStorage()));
  }

  private void init(LocalVcs v) {
    vcs = v;
    bus = Messages.newMessageBus();
    startupManager = new MyStartupManager();
    rootManager = new MyProjectRootManager();
    fileManager = new MyVirtualFileManager();

    service = new LocalVcsService(vcs, bus, startupManager, rootManager, fileManager);
  }

  @Test
  public void testUpdatingRoots() {
    roots.add(new TestVirtualFile("c:/root", null));
    bus.syncPublisher(ProjectTopics.PROJECT_ROOTS).rootsChanged(null);

    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testDoesNotUpdatingRootsOnInitialization() {
    roots.add(new TestVirtualFile("c:/root", null));
    init(new LocalVcs(new TestStorage()));

    assertFalse(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testUpdatingRootsOnStartup() {
    roots.add(new TestVirtualFile("c:/root", null));
    startupManager.runStartupActivity();

    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testCreatingFiles() {
    VirtualFile f = new TestVirtualFile("file", "content", 123L);
    fileManager.fireFileCreated(new VirtualFileEvent(null, f, null, null));

    Entry e = vcs.findEntry("file");
    assertNotNull(e);

    assertFalse(e.isDirectory());

    assertEquals("content", e.getContent());
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
    vcs.createFile("file", "old content", null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("file", "new content", 505L);
    fileManager.fireContentChanged(new VirtualFileEvent(null, f, null, null));

    Entry e = vcs.getEntry("file");
    assertEquals("new content", e.getContent());
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
    vcs.createFile("old name", "old content", null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("old name", null, null);
    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, f, VirtualFile.PROP_NAME, null, "new name"));

    assertFalse(vcs.hasEntry("old name"));

    Entry e = vcs.findEntry("new name");
    assertNotNull(e);

    assertEquals("old content", e.getContent());
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
    vcs.createFile("dir1/file", "content", null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("dir1/file", null, null);
    VirtualFile newParent = new TestVirtualFile("dir2", null);
    fileManager.fireBeforeFileMovement(new VirtualFileMoveEvent(null, f, null, newParent));

    assertFalse(vcs.hasEntry("dir1/file"));

    Entry e = vcs.findEntry("dir2/file");

    assertNotNull(e);
    assertEquals("content", e.getContent());
  }

  @Test
  public void testSkippingEventsFromAnotherProject() {
    boolean isAnyMethodCalled[] = {false};
    init(new MyLocalVcs(isAnyMethodCalled));
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
    private Runnable myRunnable;

    public void runStartupActivity() {
      myRunnable.run();
    }

    public void registerStartupActivity(Runnable r) {
      myRunnable = r;
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

    public FileSystemSynchronizer getFileSystemSynchronizer() {
      throw new UnsupportedOperationException();
    }
  }

  private class MyProjectRootManager extends ProjectRootManager {
    private ProjectFileIndex myFileIndex;

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
      myListener.fileCreated(e);
    }

    public void fireContentChanged(VirtualFileEvent e) {
      myListener.contentsChanged(e);
    }

    public void fireBeforeFileDeletion(VirtualFileEvent e) {
      myListener.beforeFileDeletion(e);
    }

    public void fireBeforePropertyChange(VirtualFilePropertyEvent e) {
      myListener.beforePropertyChange(e);
    }

    public void fireBeforeFileMovement(VirtualFileMoveEvent e) {
      myListener.beforeFileMovement(e);
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
    public void createFile(String path, String content, Long timestamp) {
      myIsAnyMethodCalled[0] = true;
    }

    @Override
    public void createDirectory(String path, Long timestamp) {
      myIsAnyMethodCalled[0] = true;
    }

    @Override
    public void changeFileContent(String path, String content, Long timestamp) {
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
