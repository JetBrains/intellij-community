package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

// todo get rid of Path parameters
public class LocalVcs {
  private Storage myStorage;

  private ChangeList myChangeList;
  private RootEntry myRoot;
  private Integer myEntryCounter;

  private List<Change> myPendingChanges = new ArrayList<Change>();

  public LocalVcs(Storage s) {
    // todo try to get rid of need to give parameter 
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

  public boolean hasEntry(String path) {
    return myRoot.hasEntry(path);
  }

  public Entry getEntry(String path) {
    return myRoot.getEntry(path);
  }

  public Entry findEntry(String path) {
    return myRoot.findEntry(path);
  }

  public List<Entry> getRoots() {
    return myRoot.getRoots();
  }

  // todo timestamp parameter is a bit ugly
  public void createFile(String path, String content, Long timestamp) {
    myPendingChanges.add(new CreateFileChange(getNextId(), path, content, timestamp));
  }

  public void createDirectory(String path, Long timestamp) {
    myPendingChanges.add(new CreateDirectoryChange(getNextId(), path, timestamp));
  }

  private Integer getNextId() {
    return myEntryCounter++;
  }

  public void changeFileContent(String path, String content, Long timestamp) {
    myPendingChanges.add(new ChangeFileContentChange(path, content, timestamp));
  }

  public void rename(String path, String newName, Long timestamp) {
    myPendingChanges.add(new RenameChange(path, newName, timestamp));
  }

  public void move(String path, String newParentPath, Long timestamp) {
    myPendingChanges.add(new MoveChange(path, newParentPath, timestamp));
  }

  public void delete(String path) {
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

    myChangeList.applyChangeSetTo(myRoot, cs);
    clearPendingChanges();
  }

  public void putLabel(String label) {
    // todo maybe join with apply method
    myChangeList.labelLastChangeSet(label);
  }

  public List<Label> getLabelsFor(String path) {
    List<Label> result = new ArrayList<Label>();

    Entry e = getEntry(path);
    ChangeList cl = myChangeList.getChangeListFor(e);

    for (ChangeSet cs : cl.getChangeSets()) {
      result.add(new Label(e, cl, cs, myRoot));
    }

    return result;
  }
}
