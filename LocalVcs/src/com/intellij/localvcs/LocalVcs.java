package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private Storage myStorage;

  // todo remove initialization
  private ChangeList myChangeList = new ChangeList();
  private RootEntry myRoot = new RootEntry();
  private Integer myEntryCounter = 0;

  private List<Change> myPendingChanges = new ArrayList<Change>();

  // todo introduce Storage class
  public LocalVcs() {}

  public LocalVcs(Storage s) {
    myStorage = s;
  }

  public void load() {
    // todo remove this method
    try {
      myChangeList = myStorage.loadChangeList();
      myRoot = myStorage.loadRootEntry();
      myEntryCounter = myStorage.loadCounter();
    } catch (IOException e) {
      // todo move exception handling to Storage class
      throw new RuntimeException(e);
    }
  }

  protected void store() {
    // make it private
    try {
      myStorage.storeChangeList(myChangeList);
      myStorage.storeRootEntry(myRoot);
      myStorage.storeCounter(myEntryCounter); // todo test it
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean hasEntry(Path path) {
    return myRoot.hasEntry(path);
  }

  public Entry getEntry(Path path) {
    return myRoot.getEntry(path);
  }

  public List<Entry> getEntryHistory(Path path) {
    //todo optimize it
    List<Entry> result = new ArrayList<Entry>();

    // todo clean up this mess
    // todo should we raise exception?
    if (!myRoot.hasEntry(path)) return result;

    Integer id = myRoot.getEntry(path).getObjectId();

    for (RootEntry r : getHistory()) {
      if (!r.hasEntry(id)) break;
      result.add(r.getEntry(id));
    }

    return result;
  }

  public void createFile(Path path, String content) {
    myPendingChanges.add(new CreateFileChange(path, content, getNextId()));
  }

  public void createDirectory(Path path) {
    myPendingChanges.add(new CreateDirectoryChange(path, getNextId()));
  }

  private Integer getNextId() {
    return myEntryCounter++;
  }

  public void changeFileContent(Path path, String content) {
    myPendingChanges.add(new ChangeFileContentChange(path, content));
  }

  public void rename(Path path, String newName) {
    myPendingChanges.add(new RenameChange(path, newName));
  }

  public void move(Path path, Path newParent) {
    myPendingChanges.add(new MoveChange(path, newParent));
  }

  public void delete(Path path) {
    myPendingChanges.add(new DeleteChange(path));
  }

  public void apply() {
    ChangeSet cs = new ChangeSet(myPendingChanges);

    myRoot = myChangeList.applyChangeSetOn(myRoot, cs);
    clearPendingChanges();
  }

  public void revert() {
    clearPendingChanges();
    myRoot = myChangeList.revertOn(myRoot);
  }

  private void clearPendingChanges() {
    myPendingChanges = new ArrayList<Change>();
  }

  public Boolean isClean() {
    return myPendingChanges.isEmpty();
  }

  public void putLabel(String label) {
    myChangeList.setLabel(myRoot, label);
  }

  public RootEntry getSnapshot(String label) {
    for (RootEntry r : getHistory()) {
      if (label.equals(myChangeList.getLabel(r))) return r;
    }
    throw new LocalVcsException();
  }

  public List<RootEntry> getHistory() {
    List<RootEntry> result = new ArrayList<RootEntry>();

    RootEntry r = myRoot;
    while (r.canBeReverted()) {
      result.add(r);
      r = myChangeList.revertOn(r);
    }

    return result;
  }
}
