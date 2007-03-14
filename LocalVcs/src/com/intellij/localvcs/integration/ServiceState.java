package com.intellij.localvcs.integration;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

// todo synchronization
public abstract class ServiceState {
  protected FileListener myOwner;
  protected ILocalVcs myVcs;
  protected IdeaGateway myGateway;

  public ServiceState(FileListener owner, ILocalVcs vcs, IdeaGateway gw) {
    myOwner = owner;
    myGateway = gw;
    myVcs = vcs;
  }

  public void startRefreshing() {
    throw new IllegalStateException();
  }

  public void finishRefreshing() {
    throw new IllegalStateException();
  }

  public void startCommand() {
    throw new IllegalStateException();
  }

  public void finishCommand() {
    throw new IllegalStateException();
  }

  protected void goToState(ServiceState s) {
    beforeExitingFromState();
    myOwner.goToState(s);
  }

  public void create(VirtualFile f) {
    try {
      createRecursively(f);
      afterEachChange();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void createRecursively(VirtualFile f) throws IOException {
    if (f.isDirectory()) {
      myVcs.createDirectory(f.getPath(), f.getTimeStamp());
      for (VirtualFile child : f.getChildren()) createRecursively(child);
    }
    else {
      myVcs.createFile(f.getPath(), physicalContentOf(f), f.getTimeStamp());
    }
  }

  public void changeFileContent(VirtualFile f) {
    try {
      myVcs.changeFileContent(f.getPath(), physicalContentOf(f), f.getTimeStamp());
      afterEachChange();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] physicalContentOf(VirtualFile f) throws IOException {
    return myGateway.getPhysicalContent(f);
  }

  public void rename(VirtualFile f, String newName) {
    myVcs.rename(f.getPath(), newName);
    afterEachChange();
  }

  public void move(VirtualFile file, VirtualFile newParent) {
    myVcs.move(file.getPath(), newParent.getPath());
    afterEachChange();
  }

  public void delete(VirtualFile f) {
    myVcs.delete(f.getPath());
    afterEachChange();
  }

  public boolean isFileContentChangedByRefresh(VirtualFile f) {
    return false;
  }

  protected void beforeExitingFromState() {
  }

  protected void afterEachChange() {
  }
}