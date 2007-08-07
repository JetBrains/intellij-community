package com.intellij.history.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.history.core.InMemoryLocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.integration.stubs.StubCommandProcessor;
import com.intellij.history.integration.stubs.StubProjectRootManagerEx;
import com.intellij.history.integration.stubs.StubStartupManagerEx;
import com.intellij.history.integration.stubs.StubVirtualFileManagerEx;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
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
  protected LocalHistoryConfiguration configuration;

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
    configuration = new LocalHistoryConfiguration();

    service = new LocalHistoryService(vcs, gateway, configuration, startupManager, rootManager, fileManager, commandProcessor);
  }

  protected LocalVcs createLocalVcs() {
    return new InMemoryLocalVcs();
  }

  private void doUpdateRoots(CacheUpdater u) {
    gateway.setContentRoots(roots.toArray(new VirtualFile[0]));
    CacheUpdaterHelper.performUpdate(u);
  }


  protected class MyStartupManager extends StubStartupManagerEx {
    private CacheUpdater myUpdater;

    @Override
    public FileSystemSynchronizer getFileSystemSynchronizer() {
      return new FileSystemSynchronizer() {
        @Override
        public void registerCacheUpdater(@NotNull CacheUpdater u) {
          myUpdater = u;
        }
      };
    }

    public void synchronizeFileSystem() {
      doUpdateRoots(myUpdater);
    }
  }

  protected class MyProjectRootManagerEx extends StubProjectRootManagerEx {
    private CacheUpdater myUpdater;

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
        doUpdateRoots(myUpdater);
      }
    }
  }

  protected class MyVirtualFileManagerEx extends StubVirtualFileManagerEx {
    private VirtualFileListener myFileListener;
    private VirtualFileManagerListener myFileManagerListener;
    private CacheUpdater myRefreshUpdater;

    public void fireBeforeRefreshStart(boolean async) {
      myFileManagerListener.beforeRefreshStart(async);
    }

    public void fireAfterRefreshFinish(boolean async) {
      myFileManagerListener.afterRefreshFinish(async);
    }

    public void fireFileCreated(VirtualFile f) {
      if (hasVirtualFileListener()) {
        myFileListener.fileCreated(new VirtualFileEvent(null, f, null, null));
      }
    }

    public void fireContentChanged(VirtualFile f) {
      if (hasVirtualFileListener()) {
        myFileListener.contentsChanged(new VirtualFileEvent(null, f, null, null));
      }
    }

    public void firePropertyChanged(VirtualFile f, String property, String oldValue) {
      if (hasVirtualFileListener()) {
        myFileListener.propertyChanged(new VirtualFilePropertyEvent(null, f, property, oldValue, null));
      }
    }

    public void fireFileDeletion(VirtualFile f) {
      if (hasVirtualFileListener()) {
        myFileListener.fileDeleted(new VirtualFileEvent(null, f, null, null));
      }
    }

    @Override
    public void addVirtualFileListener(VirtualFileListener l) {
      myFileListener = l;
    }

    @Override
    public void removeVirtualFileListener(VirtualFileListener l) {
      if (myFileListener == l) myFileListener = null;
    }

    public boolean hasVirtualFileListener() {
      return myFileListener != null;
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
    public void registerRefreshUpdater(CacheUpdater u) {
      myRefreshUpdater = u;
    }

    @Override
    public void unregisterRefreshUpdater(CacheUpdater u) {
      if (myRefreshUpdater == u) myRefreshUpdater = null;
    }

    public boolean hasRefreshUpdater() {
      return myRefreshUpdater != null;
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
