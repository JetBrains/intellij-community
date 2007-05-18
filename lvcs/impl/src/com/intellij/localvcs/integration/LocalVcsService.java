package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.Nullable;

public class LocalVcsService {
  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  // todo get rid of all this managers...
  private StartupManager myStartupManager;
  private ProjectRootManagerEx myRootManager;
  private VirtualFileManagerEx myFileManager;
  private CommandProcessor myCommandProcessor;

  private ServiceStateHolder myStateHolder;

  private FileListener myFileListener;
  private CacheUpdater myCacheUpdater;
  private FileContentProvider myFileContentProvider;

  public LocalVcsService(ILocalVcs vcs,
                         IdeaGateway gw,
                         StartupManager sm,
                         ProjectRootManagerEx rm,
                         VirtualFileManagerEx fm,
                         CommandProcessor cp) {
    myVcs = vcs;
    myGateway = gw;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
    myCommandProcessor = cp;

    myStateHolder = new ServiceStateHolder();
    myStateHolder.setState(new ListeningServiceState(myStateHolder, myVcs, myGateway));

    registerStartupActivity();
    subscribeForRootChanges();
  }

  public void shutdown() {
    myFileManager.unregisterFileContentProvider(myFileContentProvider);
    myFileManager.removeVirtualFileManagerListener(myFileListener);
    myCommandProcessor.removeCommandListener(myFileListener);
    myRootManager.unregisterChangeUpdater(myCacheUpdater);
  }

  private void registerStartupActivity() {
    FileSystemSynchronizer fs = myStartupManager.getFileSystemSynchronizer();
    fs.registerCacheUpdater(new CacheUpdaterAdaptor(new Runnable() {
      public void run() {
        registerListenersAndContentProvider();
      }
    }));
  }

  private void subscribeForRootChanges() {
    myCacheUpdater = new CacheUpdaterAdaptor(null);
    myRootManager.registerChangeUpdater(myCacheUpdater);
  }

  private void registerListenersAndContentProvider() {
    myFileListener = new FileListener(myVcs, myGateway, myStateHolder);
    myFileContentProvider = new FileContentProvider() {
      public VirtualFile[] getCoveredDirectories() {
        return myRootManager.getContentRoots();
      }

      @Nullable
      public ProvidedContent getProvidedContent(VirtualFile f) {
        return getProvidedContentFor(f);
      }

      public VirtualFileListener getVirtualFileListener() {
        return myFileListener;
      }
    };

    // todo check the order of vfm-listener
    myCommandProcessor.addCommandListener(myFileListener);
    myFileManager.addVirtualFileManagerListener(myFileListener);
    myFileManager.registerFileContentProvider(myFileContentProvider);
  }

  private ProvidedContent getProvidedContentFor(VirtualFile f) {
    if (!getFileFilter().isAllowedAndUnderContentRoot(f)) return null;

    Entry e = myVcs.findEntry(f.getPath());
    if (e == null) return null;

    Content c = e.getContent();
    return c.isAvailable() ? new EntryProvidedContent(c) : null;
  }

  public LocalHistoryAction startAction(String name) {
    LocalHistoryActionImpl a = new LocalHistoryActionImpl(myFileListener, name);
    a.start();
    return a;
  }

  private FileFilter getFileFilter() {
    return myGateway.getFileFilter();
  }

  private class CacheUpdaterAdaptor implements CacheUpdater {
    private Updater myUpdater;
    private Runnable myOnFinishTask;

    protected CacheUpdaterAdaptor(Runnable onFinishTask) {
      myOnFinishTask = onFinishTask;
    }

    public VirtualFile[] queryNeededFiles() {
      myUpdater = new Updater(myVcs, getFileFilter(), myRootManager.getContentRoots());
      return myUpdater.queryNeededFiles();
    }

    public void processFile(FileContent c) {
      myUpdater.processFile(c);
    }

    public void updatingDone() {
      myUpdater.updatingDone();
      if (myOnFinishTask != null) myOnFinishTask.run();
    }

    public void canceled() {
      myUpdater.canceled();
    }
  }
}
