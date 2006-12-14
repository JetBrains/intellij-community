package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.*;

import java.io.IOException;

public class LocalVcsService implements Disposable {
  // todo test exceptions...
  // todo use CacheUpdater to update roots

  private LocalVcs myVcs;
  private StartupManager myStartupManager;
  private ProjectRootManagerEx myRootManager;
  private VirtualFileManager myFileManager;
  private FileFilter myFileFilter;
  private VirtualFileAdapter myFileListener;

  public LocalVcsService(LocalVcs vcs, StartupManager sm, ProjectRootManagerEx rm, VirtualFileManager fm, FileFilter ff) {
    myVcs = vcs;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
    myFileFilter = ff;

    registerStartupActivity();
  }

  // todo get rid of disposable interface
  public void dispose() {
    myFileManager.removeVirtualFileListener(myFileListener);
  }

  private void registerStartupActivity() {
    FileSystemSynchronizer fs = myStartupManager.getFileSystemSynchronizer();
    fs.registerCacheUpdater(new MyCacheUpdater() {
      public void updatingDone() {
        updateRoots();
        subscribeForRootChanges();
        subscribeForFileChanges();
      }
    });
  }

  private void subscribeForRootChanges() {
    myRootManager.registerChangeUpdater(new MyCacheUpdater() {
      public void updatingDone() {
        updateRoots();
      }
    });
  }

  private void subscribeForFileChanges() {
    myFileListener = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        if (notForMe(e)) return;
        createFile(e.getFile());
      }

      @Override
      public void contentsChanged(VirtualFileEvent e) {
        if (notForMe(e)) return;
        changeFileContent(e.getFile());
      }

      @Override
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        if (notForMe(e)) return;
        if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
        rename(e.getFile(), (String)e.getNewValue());
      }

      @Override
      public void beforeFileMovement(VirtualFileMoveEvent e) {
        if (notForMe(e)) return;
        move(e.getFile(), e.getNewParent());
      }

      @Override
      public void beforeFileDeletion(VirtualFileEvent e) {
        if (notForMe(e)) return;
        delete(e.getFile());
      }

      private boolean notForMe(VirtualFileEvent e) {
        return !myFileFilter.isFileAllowed(e.getFile());
      }
    };
    myFileManager.addVirtualFileListener(myFileListener);
  }


  private void updateRoots() {
    try {
      Updater.update(myVcs, myFileFilter, myRootManager.getContentRoots());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void createFile(VirtualFile f) {
    try {
      // todo apply all changes at once
      if (f.isDirectory()) {
        myVcs.createDirectory(f.getPath(), f.getTimeStamp());
        for (VirtualFile ch : f.getChildren()) {
          createFile(ch);
        }
      }
      else {
        myVcs.createFile(f.getPath(), f.contentsToByteArray(), f.getTimeStamp());
      }
      myVcs.apply();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void changeFileContent(VirtualFile f) {
    try {
      myVcs.changeFileContent(f.getPath(), f.contentsToByteArray(), f.getTimeStamp());
      myVcs.apply();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void rename(VirtualFile f, String newName) {
    myVcs.rename(f.getPath(), newName);
    myVcs.apply();
  }

  private void move(VirtualFile f, VirtualFile newParent) {
    myVcs.move(f.getPath(), newParent.getPath());
    myVcs.apply();
  }

  private void delete(VirtualFile f) {
    myVcs.delete(f.getPath());
    myVcs.apply();
  }

  private abstract class MyCacheUpdater implements CacheUpdater {
    public VirtualFile[] queryNeededFiles() {
      return new VirtualFile[0];
    }

    public void processFile(FileContent c) {
    }

    public void canceled() {
    }
  }
}
