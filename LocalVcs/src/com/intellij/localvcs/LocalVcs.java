package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private Storage myStorage;

  private ChangeList myChangeList;
  private RootEntry myRoot;
  private Integer myEntryCounter;

  private List<Change> myPendingChanges = new ArrayList<Change>();

  public LocalVcs(Storage s) {
    myStorage = s;
    load();
  }

  private void load() {
    myChangeList = myStorage.loadChangeList();
    myRoot = myStorage.loadRootEntry();
    myEntryCounter = myStorage.loadCounter();
  }

  public void store() {
    myStorage.storeChangeList(myChangeList);
    myStorage.storeRootEntry(myRoot);
    myStorage.storeCounter(myEntryCounter);
  }

  public boolean hasEntry(Path path) {
    return myRoot.hasEntry(path);
  }

  public Entry getEntry(Path path) {
    return myRoot.getEntry(path);
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

  public Boolean isClean() {
    return myPendingChanges.isEmpty();
  }

  private void clearPendingChanges() {
    myPendingChanges = new ArrayList<Change>();
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

  public void putLabel(String label) {
    myChangeList.setLabel(myRoot, label);
  }

  //public DifferenceList getDifferenceList() {
  //  return new DifferenceList(myChangeList, myRoot);
  //}

  public List<Entry> getEntryHistory(Path path) {
    // todo remove this method
    // todo optimize me and clean up this mess

    if (!hasEntry(path)) throw new LocalVcsException();

    List<Entry> result = new ArrayList<Entry>();
    Integer id = getEntry(path).getObjectId();

    for (RootEntry r : getHistory()) {
      if (!r.hasEntry(id)) break;
      result.add(r.getEntry(id));
    }

    return result;
  }

  public List<RootEntry> getHistory() {
    // todo remove this method
    List<RootEntry> result = new ArrayList<RootEntry>();

    RootEntry r = myRoot;
    while (r.canBeReverted()) {
      result.add(r);
      r = myChangeList.revertOn(r);
    }

    return result;
  }
}
