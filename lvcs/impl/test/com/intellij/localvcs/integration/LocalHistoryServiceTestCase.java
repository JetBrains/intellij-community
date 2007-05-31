package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.TestLocalVcs;
import com.intellij.localvcs.integration.stubs.StubCommandProcessor;
import com.intellij.localvcs.integration.stubs.StubProjectRootManagerEx;
import com.intellij.localvcs.integration.stubs.StubStartupManagerEx;
import com.intellij.localvcs.integration.stubs.StubVirtualFileManagerEx;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

// todo review LocalVcsServiceTests...
public class LocalHistoryServiceTestCase extends LocalVcsTestCase {
  // todo 2 test broken storage
  // todo 3 take a look at the old-lvcs tests

  protected LocalVcs vcs;
  protected LocalHistoryService service;
  protected TestIdeaGateway gateway;
  protected List<VirtualFile> roots = new ArrayList<VirtualFile>();
  protected MyStartupManager startupManager;
  protected MyProjectRootManagerEx rootManager;
  protected MyVirtualFileManagerEx fileManager;
  protected TestFileFilter fileFilter;
  protected MyCommandProcessor commandProcessor;

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
    gateway = new TestIdeaGateway();
    fileFilter = new TestFileFilter();
    gateway.setFileFilter(fileFilter);

    startupManager = new MyStartupManager();
    rootManager = new MyProjectRootManagerEx();
    fileManager = new MyVirtualFileManagerEx();
    commandProcessor = new MyCommandProcessor();

    service = new LocalHistoryService(vcs, gateway, startupManager, rootManager, fileManager, commandProcessor);
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
    private VirtualFileManagerListener myFileManagerListener;
    private FileContentProvider myProvider;

    public void fireBeforeRefreshStart(boolean async) {
      myFileManagerListener.beforeRefreshStart(async);
    }

    public void fireAfterRefreshFinish(boolean async) {
      myFileManagerListener.afterRefreshFinish(async);
    }

    public void fireFileCreated(VirtualFile f) {
      if (getListener() != null) {
        getListener().fileCreated(new VirtualFileEvent(null, f, null, null));
      }
    }

    public void fireContentChanged(VirtualFile f) {
      if (getListener() != null) {
        getListener().contentsChanged(new VirtualFileEvent(null, f, null, null));
      }
    }

    public void firePropertyChanged(VirtualFile f, String property, String oldValue) {
      if (getListener() != null) {
        getListener().propertyChanged(new VirtualFilePropertyEvent(null, f, property, oldValue, null));
      }
    }

    public void fireFileDeletion(VirtualFile f) {
      if (getListener() != null) {
        getListener().fileDeleted(new VirtualFileEvent(null, f, null, null));
      }
    }

    private VirtualFileListener getListener() {
      return myProvider == null ? null : myProvider.getVirtualFileListener();
    }

    @Override
    public void addVirtualFileManagerListener(VirtualFileManagerListener l) {
      myFileManagerListener = l;
    }

    @Override
    public void removeVirtualFileManagerListener(VirtualFileManagerListener l) {
      if (myFileManagerListener == l) myFileManagerListener = null;
    }

    public boolean hasVirtualFileManagerListener() {
      return myFileManagerListener != null;
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

  protected class MyCommandProcessor extends StubCommandProcessor {
    CommandListener myListener;

    @Override
    public void executeCommand(Runnable runnable, String name, Object groupId) {
      CommandEvent e = new CommandEvent(this, null, name, null, null, null);

      if (hasListener()) myListener.commandStarted(e);
      runnable.run();
      if (hasListener()) myListener.commandFinished(e);
    }

    @Override
    public void addCommandListener(CommandListener l) {
      myListener = l;
    }

    @Override
    public void removeCommandListener(CommandListener l) {
      if (myListener == l) myListener = null;
    }

    public boolean hasListener() {
      return myListener != null;
    }
  }
}
