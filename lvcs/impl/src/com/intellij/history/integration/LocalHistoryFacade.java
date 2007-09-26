package com.intellij.history.integration;

import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.Reversed;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalHistoryFacade {
  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  private int myChangeSetDepth = 0;

  private List<String> myLog = new ArrayList<String>();

  public LocalHistoryFacade(ILocalVcs vcs, IdeaGateway gw) {
    myGateway = gw;
    myVcs = vcs;
  }

  public void startRefreshing() {
    log("refresh started");
    beginChangeSet();
  }

  public void finishRefreshing() {
    log("refresh finished");
    endChangeSet(LocalHistoryBundle.message("system.label.external.change"));
  }

  public void startCommand() {
    log("command started");
    beginChangeSet();
  }

  public void finishCommand(String name) {
    log("command finished: " + name);
    endChangeSet(name);
  }

  public void startAction() {
    log("action started (depth=" + myChangeSetDepth);

    if (myChangeSetDepth == 0) myVcs.beginChangeSet();
    registerUnsavedDocumentChanges();
    myVcs.endChangeSet(null);
    if (myChangeSetDepth > 0) myVcs.beginChangeSet();

    beginChangeSet();
  }

  public void finishAction(String name) {
    log("action finished: " + name);

    registerUnsavedDocumentChanges();
    endChangeSet(name);
  }

  private void registerUnsavedDocumentChanges() {
    myGateway.registerUnsavedDocuments(myVcs);
  }

  public void beginChangeSet() {
    myChangeSetDepth++;
    if (myChangeSetDepth == 1) {
      myVcs.beginChangeSet();
    }
  }

  public void endChangeSet(String name) {
    assert depthIsValid();

    myChangeSetDepth--;
    if (myChangeSetDepth == 0) {
      myVcs.endChangeSet(name);
    }
  }

  private boolean depthIsValid() {
    if (myChangeSetDepth > 0) return true;

    String log = "";
    for (String s : Reversed.list(myLog)) {
      log += s + "\n";
    }
    throw new RuntimeException(log);
  }

  public void create(VirtualFile f) {
    if (f.isDirectory()) {
      myVcs.createDirectory(f.getPath());
    }
    else {
      myVcs.createFile(f.getPath(), contentFactoryFor(f), f.getTimeStamp(), !f.isWritable());
    }
  }

  public void restore(VirtualFile f, Entry e) {
    if (f.isDirectory()) {
      myVcs.restoreDirectory(e.getId(), f.getPath());
    }
    else {
      myVcs.restoreFile(e.getId(), f.getPath(), contentFactoryFor(f), f.getTimeStamp(), e.isReadOnly());
    }
  }

  public void changeFileContent(VirtualFile f) {
    myVcs.changeFileContent(f.getPath(), contentFactoryFor(f), f.getTimeStamp());
  }

  private ContentFactory contentFactoryFor(final VirtualFile f) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() throws IOException {
        return f.contentsToByteArray();
      }

      @Override
      public long getLength() throws IOException {
        return f.getLength();
      }
    };
  }

  public void rename(VirtualFile f, String newName) {
    myVcs.rename(f.getPath(), newName);
  }

  public void changeROStatus(VirtualFile f) {
    myVcs.changeROStatus(f.getPath(), !f.isWritable());
  }

  public void move(VirtualFile file, VirtualFile newParent) {
    myVcs.move(file.getPath(), newParent.getPath());
  }

  public void delete(VirtualFile f) {
    myVcs.delete(f.getPath());
  }

  private void log(String s) {
    assert myLog.add(s);
  }
}
