package com.intellij.localvcs.core;

import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.revisions.*;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Storage;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;
import com.intellij.localvcs.integration.Clock;
import com.intellij.localvcs.integration.RevisionTimestampComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalVcs implements ILocalVcs {
  protected Storage myStorage;

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

    purgeObsolete(getPurgingPeriod());

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

  public RootEntry getRootEntry() {
    return myRoot;
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

  public void createFile(String path, ContentFactory f, long timestamp) {
    Content c = createContentFrom(f);
    applyChange(new CreateFileChange(getNextId(), path, c, timestamp));
  }

  protected Content createContentFrom(ContentFactory h) {
    return h.createContent(myStorage);
  }

  public void createDirectory(String path) {
    applyChange(new CreateDirectoryChange(getNextId(), path));
  }

  private int getNextId() {
    return myEntryCounter++;
  }

  public void changeFileContent(String path, ContentFactory f, long timestamp) {
    Content c = createContentFrom(f);
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
    applyLabel(new PutLabelChange(getCurrentTimestamp(), name, false));
  }

  public void putLabel(String path, String name) {
    applyLabel(new PutEntryLabelChange(getCurrentTimestamp(), path, name, false));
  }

  public void mark(String path) {
    applyLabel(new PutEntryLabelChange(getCurrentTimestamp(), path, "Marked", true));
  }

  private void applyLabel(PutLabelChange c) {
    c.applyTo(myRoot);
    myChangeList.addChange(c);
  }

  private void applyChange(Change c) {
    c.applyTo(myRoot);
    myPendingChanges.add(c);
    myLastChange = c;

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

  public Change getLastChange() {
    return myLastChange;
  }

  public List<Change> getChangesAfter(Change target) {
    List<Change> result = new ArrayList<Change>();

    for (Change c : Reversed.list(myPendingChanges)) {
      if (c == target) return result;
      result.add(c);
    }
    result.addAll(myChangeList.getPlainChangesAfter(target));

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

  public void purgeObsolete(long period) {
    List<Content> contentsToPurge = myChangeList.purgeObsolete(period);
    myStorage.purgeContents(contentsToPurge);
  }

  protected long getPurgingPeriod() {
    return 3 * 24 * 60 * 60 * 1000;
  }

  public byte[] getByteContent(String path, RevisionTimestampComparator c) {
    for (Revision r : getRevisionsFor(path)) {
      if (c.isSuitable(r.getTimestamp())) return getByteContentOf(r);
    }
    return null;
  }

  public byte[] getLastMarkedByteContent(String path) {
    for (Revision r : getRevisionsFor(path)) {
      if (r.isMarked()) return getByteContentOf(r);
    }
    return null;
  }

  private byte[] getByteContentOf(Revision r) {
    Content c = r.getEntry().getContent();
    return c.isAvailable() ? c.getBytes() : null;
  }

  public static class Memento {
    public RootEntry myRoot = new RootEntry();
    public int myEntryCounter = 0;
    public ChangeList myChangeList = new ChangeList();
  }
}
