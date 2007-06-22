package com.intellij.history.integration;

import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

// todo synchronization
public class LocalHistoryFacade {
  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  private int myChangeSetDepth = 0;

  public LocalHistoryFacade(ILocalVcs vcs, IdeaGateway gw) {
    myGateway = gw;
    myVcs = vcs;
  }

  public void startRefreshing() {
    beginChangeSet();
  }

  public void finishRefreshing() {
    endChangeSet("External Change");
  }

  public void startCommand() {
    beginChangeSet();
  }

  public void finishCommand(String name) {
    endChangeSet(name);
  }

  public void startAction() {
    if (myChangeSetDepth == 0) myVcs.beginChangeSet();
    registerUnsavedDocumentChanges();
    myVcs.endChangeSet(null);
    if (myChangeSetDepth > 0) myVcs.beginChangeSet();
    beginChangeSet();
  }

  public void finishAction(String name) {
    registerUnsavedDocumentChanges();
    endChangeSet(name);
  }

  private void registerUnsavedDocumentChanges() {
    myGateway.registerUnsavedDocuments(myVcs);
  }

  private void beginChangeSet() {
    myChangeSetDepth++;
    if (myChangeSetDepth == 1) {
      myVcs.beginChangeSet();
    }
  }

  private void endChangeSet(String name) {
    myChangeSetDepth--;
    if (myChangeSetDepth == 0) {
      myVcs.endChangeSet(name);
    }
  }

  public void create(VirtualFile f) {
    myVcs.beginChangeSet();
    createRecursively(f);
    myVcs.endChangeSet(null);
  }

  private void createRecursively(VirtualFile f) {
    if (f.isDirectory()) {
      myVcs.createDirectory(f.getPath());
      for (VirtualFile child : f.getChildren()) createRecursively(child);
    }
    else {
      myVcs.createFile(f.getPath(), contentFactoryFor(f), f.getTimeStamp());
    }
  }

  public void restore(VirtualFile f, Entry e) {
    if (f.isDirectory()) {
      myVcs.restoreDirectory(e.getId(), f.getPath());
    }
    else {
      myVcs.restoreFile(e.getId(), f.getPath(), contentFactoryFor(f), f.getTimeStamp());
    }
  }

  public void changeFileContent(VirtualFile f) {
    myVcs.changeFileContent(f.getPath(), contentFactoryFor(f), f.getTimeStamp());
  }

  private ContentFactory contentFactoryFor(final VirtualFile f) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() throws IOException {
        return myGateway.getPhysicalContent(f);
      }

      @Override
      public long getLength() throws IOException {
        return myGateway.getPhysicalLength(f);
      }
    };
  }

  public void rename(VirtualFile f, String newName) {
    myVcs.rename(f.getPath(), newName);
  }

  public void move(VirtualFile file, VirtualFile newParent) {
    myVcs.move(file.getPath(), newParent.getPath());
  }

  public void delete(VirtualFile f) {
    myVcs.delete(f.getPath());
  }
}