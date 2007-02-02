package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.Nullable;

public class LocalVcsService {
  // todo test exceptions...
  // todo extract inner classes

  private ILocalVcs myVcs;
  private StartupManager myStartupManager;
  private ProjectRootManagerEx myRootManager;
  private VirtualFileManagerEx myFileManager;
  private LocalFileSystem myFileSystem;
  private FileDocumentManager myDocumentManager;
  private FileFilter myFileFilter;
  private VirtualFileListener myFileListener;
  private CacheUpdater myCacheUpdater;
  private FileContentProvider myFileContentProvider;

  public LocalVcsService(ILocalVcs vcs,
                         StartupManager sm,
                         ProjectRootManagerEx rm,
                         VirtualFileManagerEx fm,
                         LocalFileSystem fs,
                         FileDocumentManager dm,
                         FileFilter f) {
    myVcs = vcs;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
    myFileSystem = fs;
    myDocumentManager = dm;
    myFileFilter = f;

    registerStartupActivity();
    subscribeForRootChanges();
  }

  public void shutdown() {
    myFileManager.unregisterFileContentProvider(myFileContentProvider);
    myRootManager.unregisterChangeUpdater(myCacheUpdater);
  }

  private void registerStartupActivity() {
    FileSystemSynchronizer fs = myStartupManager.getFileSystemSynchronizer();
    fs.registerCacheUpdater(new CacheUpdaterAdaptor(new Runnable() {
      public void run() {
        registerFileListenerAndContentProvider();
      }
    }));
  }

  private void subscribeForRootChanges() {
    myCacheUpdater = new CacheUpdaterAdaptor(null);
    myRootManager.registerChangeUpdater(myCacheUpdater);
  }

  private void registerFileListenerAndContentProvider() {
    myFileListener = new FileListener(myVcs, myFileFilter, myFileSystem);

    myFileContentProvider = new FileContentProvider() {
      public VirtualFile[] getCoveredDirectories() {
        return myRootManager.getContentRoots();
      }

      @Nullable
      public ProvidedContent getProvidedContent(VirtualFile f) {
        Entry e = myVcs.findEntry(f.getPath());
        if (e == null) return null;
        if (e.getContent().isTooLong()) return null;
        return new EntryProvidedContent(e);
      }

      public VirtualFileListener getVirtualFileListener() {
        return myFileListener;
      }
    };
    myFileManager.registerFileContentProvider(myFileContentProvider);
  }

  public LocalVcsAction startAction(String label) {
    LocalVcsAction a = new LocalVcsAction(myVcs, myDocumentManager, myFileFilter, label);
    a.start();
    return a;
  }

  private class CacheUpdaterAdaptor implements CacheUpdater {
    private Updater myUpdater;
    private Runnable myOnFinishTask;

    protected CacheUpdaterAdaptor(Runnable onFinishTask) {
      myOnFinishTask = onFinishTask;
    }

    public VirtualFile[] queryNeededFiles() {
      myUpdater = new Updater(myVcs, myFileFilter, myRootManager.getContentRoots());
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
