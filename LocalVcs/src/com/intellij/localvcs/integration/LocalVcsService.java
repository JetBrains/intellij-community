package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
  private FileDocumentManager myDocumentManager;
  private FileFilter myFileFilter;
  private VirtualFileListener myFileListener;
  private CacheUpdater myCacheUpdater;
  private FileContentProvider myFileContentProvider;

  public LocalVcsService(ILocalVcs vcs, StartupManager sm, ProjectRootManagerEx rm, VirtualFileManagerEx fm, FileDocumentManager dm,
                         FileFilter f) {
    myVcs = vcs;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
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
        updateRoots();
        subscribeForRootChanges();
        registerFileContentProvider();
      }
    });
  }

  private void subscribeForRootChanges() {
    myCacheUpdater = new CacheUpdaterAdaptor() {
      public void updatingDone() {
        updateRoots();
      }
    };
    myRootManager.registerChangeUpdater(myCacheUpdater);
  }

  private void registerFileContentProvider() {
    myFileListener = new FileListener(myVcs, myFileFilter);

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

  private void updateRoots() {
    try {
      Updater.update(myVcs, myFileFilter, myRootManager.getContentRoots());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public LocalVcsAction startAction() {
    LocalVcsAction a = new LocalVcsAction(myVcs, myDocumentManager, myFileFilter);
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
