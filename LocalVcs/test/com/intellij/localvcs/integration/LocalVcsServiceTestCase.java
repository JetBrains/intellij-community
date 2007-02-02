package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.localvcs.integration.stubs.StubProjectRootManagerEx;
import com.intellij.localvcs.integration.stubs.StubStartupManagerEx;
import com.intellij.localvcs.integration.stubs.StubVirtualFileManagerEx;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

// todo review LocalVcsServiceTests...
public class LocalVcsServiceTestCase extends MockedLocalFileSystemTestCase {
  // todo 2 test broken storage
  // todo 3 take a look at the old-lvcs tests

  protected LocalVcs vcs;
  protected LocalVcsService service;
  protected List<VirtualFile> roots = new ArrayList<VirtualFile>();
  protected MyStartupManager startupManager;
  protected MyProjectRootManagerEx rootManager;
  protected MyVirtualFileManagerEx fileManager;
  protected TestFileFilter fileFilter;
  protected TestFileDocumentManager documentManager;

  @Before
  public void initAndStartup() {
    initAndStartup(createLocalVcs());
  }

  protected void initAndStartup(LocalVcs v) {
    initWithoutStartup(v);
    startupService();
  }

  protected void startupService() {
    startupManager.synchronizeFileSystem();
  }

  protected void initWithoutStartup(LocalVcs v) {
    vcs = v;
    startupManager = new MyStartupManager();
    rootManager = new MyProjectRootManagerEx();
    fileManager = new MyVirtualFileManagerEx();
    fileFilter = new TestFileFilter();
    documentManager = new TestFileDocumentManager();

    service = new LocalVcsService(vcs, startupManager, rootManager, fileManager, fileSystem, documentManager, fileFilter);
  }

  protected LocalVcs createLocalVcs() {
    return new TestLocalVcs();
  }

  protected class MyStartupManager extends StubStartupManagerEx {
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
      CacheUpdaterHelper.performUpdate(myUpdater);
    }
  }

  protected class MyProjectRootManagerEx extends StubProjectRootManagerEx {
    private CacheUpdater myUpdater;

    @Override
    public VirtualFile[] getContentRoots() {
      return roots.toArray(new VirtualFile[0]);
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
      if (myUpdater != null) {
        CacheUpdaterHelper.performUpdate(myUpdater);
      }
    }
  }

  protected class MyVirtualFileManagerEx extends StubVirtualFileManagerEx {
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
}
