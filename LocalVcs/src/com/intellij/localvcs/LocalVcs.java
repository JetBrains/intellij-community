package com.intellij.localvcs;

import java.util.*;

// todo bulk update support - add startAction method
public class LocalVcs implements ILocalVcs {
  private Storage myStorage;

  private ChangeList myChangeList;
  private RootEntry myRoot;
  private Integer myEntryCounter;

  // todo change type to something else (for example to LinkedList)
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

  public void save() {
    purgeUpTo(getCurrentTimestamp() - getPurgingInterval());

    myStorage.storeChangeList(myChangeList);
    myStorage.storeRootEntry(myRoot);
    myStorage.storeCounter(myEntryCounter);
    myStorage.save();
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

  public void createFile(String path, byte[] content, Long timestamp) {
    Content c = contentFromString(content);
    myPendingChanges.add(new CreateFileChange(getNextId(), path, c, timestamp));
  }

  private Content contentFromString(byte[] data) {
    try {
      // todo review: this is only for tests
      if (data == null) return null;
      return myStorage.createContent(data);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void createDirectory(String path, Long timestamp) {
    myPendingChanges.add(new CreateDirectoryChange(getNextId(), path, timestamp));
  }

  private Integer getNextId() {
    return myEntryCounter++;
  }

  public void changeFileContent(String path, byte[] content, Long timestamp) {
    Content c = contentFromString(content);
    myPendingChanges.add(new ChangeFileContentChange(path, c, timestamp));
  }

  public void rename(String path, String newName) {
    myPendingChanges.add(new RenameChange(path, newName));
  }

  public void move(String path, String newParentPath) {
    myPendingChanges.add(new MoveChange(path, newParentPath));
  }

  public void delete(String path) {
    myPendingChanges.add(new DeleteChange(path));
  }

  protected Boolean isClean() {
    return myPendingChanges.isEmpty();
  }

  private void clearPendingChanges() {
    myPendingChanges = new ArrayList<Change>();
  }

  public void apply() {
    ChangeSet cs = new ChangeSet(getCurrentTimestamp(), myPendingChanges);

    myChangeList.applyChangeSetTo(myRoot, cs);
    clearPendingChanges();
  }

  public long getCurrentTimestamp() {
    return System.currentTimeMillis();
  }

  public void putLabel(String label) {
    // todo maybe join with apply method?
    myChangeList.labelLastChangeSet(label);
  }

  public List<Label> getLabelsFor(String path) {
    List<Label> result = new ArrayList<Label>();

    Entry e = getEntry(path);

    for (ChangeSet cs : myChangeList.getChangeSetsFor(e)) {
      result.add(new Label(e, myRoot, myChangeList, cs));
    }

    if (result.isEmpty()) {
      result.add(new CurrentLabel(e, getCurrentTimestamp()));
    }

    Collections.reverse(result);
    return result;
  }

  public void purgeUpTo(long timestamp) {
    List<Content> contentsToPurge = myChangeList.purgeUpTo(timestamp);
    myStorage.purgeContents(contentsToPurge);
  }

  protected long getPurgingInterval() {
    GregorianCalendar c = new GregorianCalendar();
    c.setTimeInMillis(0);
    c.add(Calendar.DAY_OF_YEAR, 5);
    return c.getTimeInMillis();
  }
}
