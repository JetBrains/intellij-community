package com.intellij.localvcs.integration;

import com.intellij.ProjectTopics;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import java.io.IOException;

public class LocalVcsService {
  // todo test exceptions...
  private LocalVcs myVcs;
  private ProjectRootManager myRootManager;
  private VirtualFileManager myFileManager;
  private MessageBusConnection myConnection;

  public LocalVcsService(LocalVcs vcs, MessageBus b, ProjectRootManager rm, VirtualFileManager fm) {
    myVcs = vcs;
    myRootManager = rm;
    myFileManager = fm;
    myConnection = b.connect();

    subscribeToRootChanges();
    subscribeToFileChanges();
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
    myFileManager.addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        createFile(e.getFile());
      }

      @Override
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
        renameFile(e.getFile(), (String)e.getNewValue());
      }

      @Override
      public void beforeFileMovement(VirtualFileMoveEvent e) {
        moveFile(e.getFile(), e.getNewParent());
      }

      @Override
      public void beforeFileDeletion(VirtualFileEvent e) {
        deleteFile(e.getFile());
      }
    });
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

  private void renameFile(VirtualFile f, String newName) {
    myVcs.rename(f.getPath(), newName, f.getTimeStamp());
    myVcs.apply();
  }

  private void moveFile(VirtualFile f, VirtualFile newParent) {
    myVcs.move(f.getPath(), newParent.getPath(), f.getTimeStamp());
    myVcs.apply();
  }

  private void deleteFile(VirtualFile f) {
    myVcs.delete(f.getPath());
    myVcs.apply();
  }
}
