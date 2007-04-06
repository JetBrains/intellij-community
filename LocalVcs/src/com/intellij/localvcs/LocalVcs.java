package com.intellij.localvcs;

import java.util.*;

public class LocalVcs implements ILocalVcs {
  private Storage myStorage;

  private ChangeList myChangeList;
  private RootEntry myRoot;
  private int myEntryCounter;

  private int myInnerChangeSetCounter = 0;
  private boolean wasSaveRequestedDuringChangeSet = false;

  private List<Change> myPendingChanges = new ArrayList<Change>();

  public LocalVcs(Storage s) {
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
    // todo a bit of hack... move it to service state
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
    // todo hack. remove after moving update into service state
    myInnerChangeSetCounter++;
  }

  public void endChangeSet(String label) {
    // todo hack. remove after moving update into service state
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

  public void createFile(String path, byte[] content, long timestamp) {
    Content c = contentFromString(content);
    applyChange(new CreateFileChange(getNextId(), path, c, timestamp));
  }

  private Content contentFromString(byte[] data) {
    return myStorage.storeContent(data);
  }

  public void createDirectory(String path) {
    applyChange(new CreateDirectoryChange(getNextId(), path));
  }

  private int getNextId() {
    return myEntryCounter++;
  }

  public void changeFileContent(String path, byte[] content, long timestamp) {
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
    if (myPendingChanges.isEmpty()) return;

    ChangeSet cs = new ChangeSet(getCurrentTimestamp(), label, myPendingChanges);
    myChangeList.addChange(cs);
    clearPendingChanges();
  }

  private void clearPendingChanges() {
    myPendingChanges = new ArrayList<Change>();
  }

  protected ChangeList getChangeList() {
    return myChangeList;
  }

  protected Boolean isClean() {
    return myPendingChanges.isEmpty();
  }

  private long getCurrentTimestamp() {
    return Clock.getCurrentTimestamp();
  }

  public List<Label> getLabelsFor(String path) {
    Entry e = getEntry(path);

    List<Change> cc = myChangeList.getChangesFor(myRoot, e.getPath());

    // todo this hack with names and timestamps is here
    // todo until I separate revisions from changesets.

    if (cc.isEmpty()) {
      CurrentLabel l = new CurrentLabel(e, null, getCurrentTimestamp());
      return Collections.<Label>singletonList(l);
    }

    List<Label> result = new ArrayList<Label>();

    Change next = cc.get(0);
    result.add(new CurrentLabel(e, next.getName(), next.getTimestamp()));

    for (int i = 0; i < cc.size() - 1; i++) {
      Change c = cc.get(i);
      next = cc.get(i + 1);
      result.add(new Label(e, myRoot, myChangeList, c, next.getName(), next.getTimestamp()));
    }

    Change last = cc.get(cc.size() - 1);
    if (!last.isCreationalFor(e)) {
      result.add(new Label(e, myRoot, myChangeList, last, null, last.getTimestamp()));
    }

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
    public int myEntryCounter = 0;
    public ChangeList myChangeList = new ChangeList();
  }
}
