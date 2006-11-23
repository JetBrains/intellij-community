package com.intellij.localvcs.integration;

import com.intellij.ProjectTopics;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Messages;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LocalVcsServiceTest extends Assert {
  private LocalVcs vcs;
  private MessageBus bus;
  private LocalVcsService service;
  private List<VirtualFile> roots = new ArrayList<VirtualFile>();
  private MyVirtualFileManager fileManager;


  @Before
  public void setUp() {
    vcs = new LocalVcs(new TestStorage());
    bus = Messages.newMessageBus();
    ProjectRootManager rm = new MyProjectRootManager();
    fileManager = new MyVirtualFileManager();

    service = new LocalVcsService(vcs, bus, rm, fileManager);
  }

  @Test
  public void testUpdatingRoots() {
    roots.add(new TestVirtualFile("c:/root", null));
    bus.syncPublisher(ProjectTopics.PROJECT_ROOTS).rootsChanged(null);

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

    // todo i'm not shure about timestamps here
    VirtualFile f = new TestVirtualFile("old name", null, 777L);
    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, f, VirtualFile.PROP_NAME, null, "new name"));

    assertFalse(vcs.hasEntry("old name"));

    Entry e = vcs.findEntry("new name");
    assertNotNull(e);

    assertEquals("old content", e.getContent());
    assertEquals(777L, e.getTimestamp());
  }

  @Test
  public void testDoNothingOnAnotherPropertyChanges() throws Exception {
    try {
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

    VirtualFile f = new TestVirtualFile("dir1/file", null, 777L);
    VirtualFile newParent = new TestVirtualFile("dir2", null);
    fileManager.fireBeforeFileMovement(new VirtualFileMoveEvent(null, f, null, newParent));

    assertFalse(vcs.hasEntry("dir1/file"));

    Entry e = vcs.findEntry("dir2/file");

    assertNotNull(e);
    assertEquals("content", e.getContent());
    assertEquals(777L, e.getTimestamp());
  }

  // todo test releasing of resources
  // todo test filtering events from another project

  private class MyProjectRootManager extends ProjectRootManager {
    @NotNull
    public VirtualFile[] getContentRoots() {
      return roots.toArray(new VirtualFile[0]);
    }

    @NotNull
    public ProjectFileIndex getFileIndex() {
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
  }

  private class MyVirtualFileManager extends VirtualFileManager {
    private VirtualFileListener myListener;

    public void addVirtualFileListener(@NotNull VirtualFileListener l) {
      myListener = l;
    }

    public void fireFileCreated(VirtualFileEvent e) {
      myListener.fileCreated(e);
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

    public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
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
}
