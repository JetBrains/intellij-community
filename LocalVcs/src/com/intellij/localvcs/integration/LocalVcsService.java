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

import java.io.IOException;

public class LocalVcsService {
  // todo test exceptions...
  // todo use CacheUpdater to update roots
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

    // todo review startup order
    registerStartupActivity();
  }

  public void shutdown() {
    myFileManager.unregisterFileContentProvider(myFileContentProvider);
    myRootManager.unregisterChangeUpdater(myCacheUpdater);
  }

  private void registerStartupActivity() {
    FileSystemSynchronizer fs = myStartupManager.getFileSystemSynchronizer();
    fs.registerCacheUpdater(new CacheUpdaterAdaptor() {
      public void updatingDone() {
        updateRoots(true);
        subscribeForRootChanges();
        registerFileContentProvider();
      }
    });
  }

  private void updateRoots(boolean performFullUpdate) {
    try {
      Updater.update(myVcs, myFileSystem, myFileFilter, performFullUpdate, myRootManager.getContentRoots());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void subscribeForRootChanges() {
    myCacheUpdater = new CacheUpdaterAdaptor() {
      public void updatingDone() {
        updateRoots(false);
      }
    };
    myRootManager.registerChangeUpdater(myCacheUpdater);
  }

  private void registerFileContentProvider() {
    myFileListener = new FileListener(myVcs, myFileFilter, myFileSystem);

    myFileContentProvider = new FileContentProvider() {
      public VirtualFile[] getCoveredDirectories() {
        return myRootManager.getContentRoots();
      }

      @Nullable
      public ProvidedContent getProvidedContent(VirtualFile f) {
        Entry e = myVcs.findEntry(f.getPath());
        return e == null ? null : new EntryContent(e);
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

  private abstract class CacheUpdaterAdaptor implements CacheUpdater {
    public VirtualFile[] queryNeededFiles() {
      return new VirtualFile[0];
    }

    public void processFile(FileContent c) {
    }

    public void canceled() {
    }
  }
}
