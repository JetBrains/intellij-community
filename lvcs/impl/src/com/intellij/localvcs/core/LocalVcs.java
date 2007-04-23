package com.intellij.localvcs.core;

import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.revisions.*;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Storage;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

import java.util.*;

public class LocalVcs implements ILocalVcs {
  private Storage myStorage;

  private ChangeList myChangeList;
  private RootEntry myRoot;
  private int myEntryCounter;

  private int myInnerChangeSetCounter = 0;
  private boolean wasSaveRequestedDuringChangeSet = false;

  private List<Change> myPendingChanges = new ArrayList<Change>();
  private Change myLastChange;

  public LocalVcs(Storage s) {
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

  public void endChangeSet(String name) {
    // todo hack. remove after moving update into service state
    if (myInnerChangeSetCounter == 1) registerChangeSet(name);
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

  public void putLabel(String name) {
    applyLabel(new PutLabelChange(getCurrentTimestamp(), name));
  }

  public void putLabel(String path, String name) {
    applyLabel(new PutEntryLabelChange(path, getCurrentTimestamp(), name));
  }

  private void applyLabel(Change c) {
    c.applyTo(myRoot);
    myChangeList.addChange(c);
  }

  private void applyChange(Change c) {
    c.applyTo(myRoot);
    myPendingChanges.add(c);
    if (c.isGlobal()) myLastChange = c;

    // todo forbid the ability of making changes outside of changeset
    if (!isInChangeSet()) registerChangeSet(null);
  }

  private void registerChangeSet(String name) {
    if (myPendingChanges.isEmpty()) return;

    Change c = new ChangeSet(getCurrentTimestamp(), name, myPendingChanges);
    myChangeList.addChange(c);
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

  public Change getLastGlobalChange() {
    return myLastChange;
  }

  public List<Change> getChangesAfter(Change target) {
    List<Change> result = new ArrayList<Change>();

    for (Change c : Reversed.list(myPendingChanges)) {
      if (c == target) return result;
      result.add(c);
    }
    result.addAll(myChangeList.getChangesAfter(target));

    return result;
  }

  private long getCurrentTimestamp() {
    return Clock.getCurrentTimestamp();
  }

  public List<Revision> getRevisionsFor(String path) {
    Entry e = getEntry(path);

    List<Change> cc = myChangeList.getChangesFor(myRoot, e.getPath());

    if (cc.isEmpty()) {
      Revision r = new CurrentRevision(e, getCurrentTimestamp());
      return Collections.singletonList(r);
    }

    List<Revision> result = new ArrayList<Revision>();
    for (Change c : cc) {
      Revision r;
      if (c.isLabel()) {
        r = new LabeledRevision(e, myRoot, myChangeList, c);
      }
      else {
        r = new RevisionAfterChange(e, myRoot, myChangeList, c);
      }
      result.add(r);
    }

    Change lastChange = cc.get(cc.size() - 1);
    if (!lastChange.isLabel() && !lastChange.isCreationalFor(e)) {
      result.add(new RevisionBeforeChange(e, myRoot, myChangeList, lastChange));
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

  public byte[] getByteContentAt(String path, long timestamp) {
    getEntry(path);
    return null;
  }

  public static class Memento {
    public RootEntry myRoot = new RootEntry();
    public int myEntryCounter = 0;
    public ChangeList myChangeList = new ChangeList();
  }
}
