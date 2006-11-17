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
    myPendingChanges.add(new CreateFileChange(getNextId(), path, content));
  }

  public void createDirectory(Path path) {
    myPendingChanges.add(new CreateDirectoryChange(getNextId(), path));
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

    myChangeList.applyChangeSetOn_old(myRoot, cs);
    clearPendingChanges();
  }

  public void _revert() {
    clearPendingChanges();
    myRoot = myChangeList.revertOn_old(myRoot);
  }

  public void putLabel(String label) {
    // todo maybe join with apply method
    myChangeList.labelLastChangeSet(label);
  }

  public List<Label> getLabelsFor(Path path) {
    List<Label> result = new ArrayList<Label>();

    Entry e = getEntry(path);
    ChangeList cl = myChangeList.getChangeListFor(e);

    for (ChangeSet cs : cl.getChangeSets()) {
      result.add(new Label(e, cl, cs, myRoot));
    }

    return result;
  }
}
