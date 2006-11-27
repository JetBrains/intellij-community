package com.intellij.localvcs.integration;

import com.intellij.ProjectTopics;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import java.io.IOException;

public class LocalVcsService implements Disposable {
  // todo test exceptions...
  // todo use CacheUpdater to update roots
  
  private LocalVcs myVcs;
  private StartupManager myStartupManager;
  private ProjectRootManager myRootManager;
  private VirtualFileManager myFileManager;
  private MessageBusConnection myConnection;
  private VirtualFileAdapter myFileListener;

  public LocalVcsService(LocalVcs vcs, MessageBus b, StartupManager sm, ProjectRootManager rm, VirtualFileManager fm) {
    myVcs = vcs;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
    myConnection = b.connect();

    subscribeToStartupManager();
    subscribeToRootChanges();
    subscribeToFileChanges();
  }

  public void dispose() {
    myFileManager.removeVirtualFileListener(myFileListener);
  }

  private void subscribeToStartupManager() {
    myStartupManager.registerStartupActivity(new Runnable() {
      public void run() {
        updateRoots();
      }
    });
  }

  private void subscribeToRootChanges() {
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        updateRoots();
      }
    });
  }

  private void subscribeToFileChanges() {
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
        return myRootManager.getFileIndex().getModuleForFile(e.getFile()) == null;
      }
    };
    myFileManager.addVirtualFileListener(myFileListener);
  }


  private void updateRoots() {
    try {
      Updater.update(myVcs, myRootManager.getContentRoots());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void createFile(VirtualFile f) {
    try {
      if (f.isDirectory()) {
        myVcs.createDirectory(f.getPath(), f.getTimeStamp());
      }
      else {
        String content = new String(f.contentsToByteArray());
        myVcs.createFile(f.getPath(), content, f.getTimeStamp());
      }
      myVcs.apply();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void changeFileContent(VirtualFile f) {
    try {
      String content = new String(f.contentsToByteArray());
      myVcs.changeFileContent(f.getPath(), content, f.getTimeStamp());
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
}
