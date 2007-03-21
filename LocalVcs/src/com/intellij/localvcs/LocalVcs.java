package com.intellij.localvcs;

import java.util.*;

public class LocalVcs implements ILocalVcs {
  private LocalVcsStorage myStorage;

  private ChangeList myChangeList;
  private RootEntry myRoot;
  private Integer myEntryCounter;

  private int myInnerChangeSetCounter = 0;
  private boolean wasSaveRequestedDuringChangeSet = false;

  // todo change type to something else (for example to LinkedList)
  private List<Change> myPendingChanges = new ArrayList<Change>();

  public LocalVcs(LocalVcsStorage s) {
    // todo try to get rid of need to pass the parameter
    myStorage = s;
    load();
  }

  private void load() {
    Memento m = myStorage.load();

    myRoot = m.myRoot;
    myEntryCounter = m.myEntryCounter;
    myChangeList = m.myChangeList;
  }

  public void save() {
    if (shouldPostpondSave()) return;

    purgeUpTo(getCurrentTimestamp() - getPurgingInterval());

    Memento m = new Memento();
    m.myRoot = myRoot;
    m.myEntryCounter = myEntryCounter;
    m.myChangeList = myChangeList;

    myStorage.store(m);
    myStorage.save();
  }

  private boolean shouldPostpondSave() {
    // todo a bit of hack... move it to service states
    if (!isInChangeSet()) return false;

    wasSaveRequestedDuringChangeSet = true;
    return true;
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

  public void beginChangeSet() {
    myInnerChangeSetCounter++;
  }

  public void endChangeSet(String label) {
    if (myInnerChangeSetCounter == 1) registerChangeSet(label);
    myInnerChangeSetCounter--;
    if (!isInChangeSet()) doPostpondedSave();
  }

  private void doPostpondedSave() {
    // todo the easiest way to do that i need, but still a hack...
    if (!wasSaveRequestedDuringChangeSet) return;
    save();
    wasSaveRequestedDuringChangeSet = false;
  }

  private boolean isInChangeSet() {
    return myInnerChangeSetCounter > 0;
  }

  public void createFile(String path, byte[] content, Long timestamp) {
    Content c = contentFromString(content);
    applyChange(new CreateFileChange(getNextId(), path, c, timestamp));
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
    applyChange(new CreateDirectoryChange(getNextId(), path, timestamp));
  }

  private Integer getNextId() {
    return myEntryCounter++;
  }

  public void changeFileContent(String path, byte[] content, Long timestamp) {
    Content c = contentFromString(content);
    applyChange(new ChangeFileContentChange(path, c, timestamp));
  }

  public void rename(String path, String newName) {
    applyChange(new RenameChange(path, newName));
  }

  public void move(String path, String newParentPath) {
    applyChange(new MoveChange(path, newParentPath));
  }

  public void delete(String path) {
    applyChange(new DeleteChange(path));
  }

  private void applyChange(Change c) {
    c.applyTo(myRoot);
    myPendingChanges.add(c);

    // todo forbid the ability of making changes outside of changeset
    if (!isInChangeSet()) registerChangeSet(null);
  }

  private void registerChangeSet(String label) {
    ChangeSet cs = new ChangeSet(getCurrentTimestamp(), label, myPendingChanges);
    myChangeList.addChangeSet(cs);
    clearPendingChanges();
  }

  private void clearPendingChanges() {
    myPendingChanges = new ArrayList<Change>();
  }

  protected Boolean isClean() {
    return myPendingChanges.isEmpty();
  }

  private long getCurrentTimestamp() {
    return Clock.getCurrentTimestamp();
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

  public static class Memento {
    public RootEntry myRoot = new RootEntry();
    public Integer myEntryCounter = 0;
    public ChangeList myChangeList = new ChangeList();
  }
}
