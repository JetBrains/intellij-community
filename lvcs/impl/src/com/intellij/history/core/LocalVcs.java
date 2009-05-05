package com.intellij.history.core;

import com.intellij.diagnostic.Diagnostic;
import com.intellij.history.ByteContent;
import com.intellij.history.Clock;
import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.Label;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.revisions.RevisionAfterChange;
import com.intellij.history.core.revisions.RevisionBeforeChange;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private final JBLock myEntriesReadLock;
  private final JBLock myEntriesWriteLock;
  private final JBLock myChangeSetsReadLock;
  private final JBLock myChangeSetsWriteLock;

  protected final Storage myStorage;

  private ChangeList myChangeList;
  private volatile int myEntryCounter;
  private Entry myRoot;

  private Change myLastChange;
  private boolean wasModifiedAfterLastSave;
  private int myChangeSetDepth;

  public LocalVcs(Storage s) {
    JBReentrantReadWriteLock entriesLock = LockFactory.createReadWriteLock();
    myEntriesReadLock = entriesLock.readLock();
    myEntriesWriteLock = entriesLock.writeLock();

    JBReentrantReadWriteLock changeSetsLock = LockFactory.createReadWriteLock();
    myChangeSetsReadLock = changeSetsLock.readLock();
    myChangeSetsWriteLock = changeSetsLock.writeLock();

    myStorage = s;

    load();
  }

  private void load() {
    writeAll();
    try {
      Memento m = myStorage.load();
      myRoot = m.myRoot;
      myEntryCounter = m.myEntryCounter;
      myChangeList = m.myChangeList;
    }
    finally {
      unwriteAll();
    }
  }

  public void save() {
    readAll();
    try {
      if (!wasModifiedAfterLastSave) return;

      // saving contents first is necessary to prevent 'storage corrupted' messages
      // if state was saved but contents
      myStorage.saveContents();

      Memento m = new Memento();
      m.myRoot = myRoot;
      m.myEntryCounter = myEntryCounter;
      m.myChangeList = myChangeList;
      myStorage.saveState(m);

      wasModifiedAfterLastSave = false;
    }
    finally {
      unreadAll();
    }
  }

  public void purgeObsoleteAndSave(long period) {
    writeAll();
    try {
      wasModifiedAfterLastSave = true;
      purgeObsolete(period);
      save();
    }
    finally {
      unwriteAll();
    }
  }

  private void purgeObsolete(long period) {
    List<Content> contentsToPurge = myChangeList.purgeObsolete(period);
    myStorage.purgeContents(contentsToPurge);
  }

  public boolean hasEntry(String path) {
    readEntries();
    try {
      return myRoot.hasEntry(path);
    }
    finally {
      unreadEntries();
    }
  }

  public Entry getEntry(String path) {
    readEntries();
    try {
      return myRoot.getEntry(path);
    }
    finally {
      unreadEntries();
    }
  }

  public Entry findEntry(String path) {
    readEntries();
    try {
      return myRoot.findEntry(path);
    }
    finally {
      unreadEntries();
    }
  }

  public List<Entry> getRoots() {
    readEntries();
    try {
      return myRoot.getChildren();
    }
    finally {
      unreadEntries();
    }
  }

  public void beginChangeSet() {
    writeChanges();
    try {
      myChangeList.beginChangeSet();
      myChangeSetDepth++;
    }
    finally {
      unwriteChanges();
    }
  }

  public void endChangeSet(String name) {
    writeChanges();
    try {
      myChangeList.endChangeSet(name);

      // we must call Storage.save to make it flush all the changes made during changeset.
      // otherwise the ContentStorage may become corrupted if IDEA is shutdown forcefully.
      myChangeSetDepth--;
      if (myChangeSetDepth == 0) {
        myStorage.saveContents();
      }
    }
    finally {
      unwriteChanges();
    }
  }

  public void createFile(String path, ContentFactory f, long timestamp, boolean isReadOnly) {
    writeAll();
    try {
      doCreateFile(getNextId(), path, f, timestamp, isReadOnly);
    }
    finally {
      unwriteAll();
    }
  }

  public void restoreFile(int id, String path, ContentFactory f, long timestamp, boolean isReadOnly) {
    writeAll();
    try {
      doCreateFile(id, path, f, timestamp, isReadOnly);
    }
    finally {
      unwriteAll();
    }
  }

  private void doCreateFile(int id, String path, ContentFactory f, long timestamp, boolean isReadOnly) {
    // todo hook for IDEADEV-21269 (and, probably, for some others).
    String parent = Paths.getParentOf(path);
    if (parent != null /*in unit test mode */ && !checkEntryExists(parent)) return;

    Content c = createContentFrom(f);
    applyChange(new CreateFileChange(id, path, c, timestamp, isReadOnly));
  }

  protected Content createContentFrom(ContentFactory f) {
    return f.createContent(myStorage);
  }

  public void createDirectory(String path) {
    writeAll();
    try {
      doCreateDirectory(getNextId(), path);
    }
    finally {
      unwriteAll();
    }
  }

  public void restoreDirectory(int id, String path) {
    writeAll();
    try {
      doCreateDirectory(id, path);
    }
    finally {
      unwriteAll();
    }
  }

  private void doCreateDirectory(int id, String path) {
    applyChange(new CreateDirectoryChange(id, path));
  }

  private int getNextId() {
    return myEntryCounter++;
  }

  public void changeFileContent(String path, ContentFactory f, long timestamp) {
    writeAll();
    try {
      // todo hook for IDEADEV-21269 (and, probably, for some others).
      if (!checkEntryExists(path)) return;

      if (contentWasNotChanged(path, f)) return;
      Content c = createContentFrom(f);
      applyChange(new ContentChange(path, c, timestamp));
    }
    finally {
      unwriteAll();
    }
  }

  private boolean checkEntryExists(String path) {
    if (!Diagnostic.isJavaAssertionsEnabled()) return true;

    if (findEntry(path) != null) return true;
    LocalHistoryLog.LOG.warn("Entry not found: " + path);
    return false;
  }

  protected boolean contentWasNotChanged(String path, ContentFactory f) {
    return f.equalsTo(getEntry(path).getContent());
  }

  public void rename(String path, String newName) {
    writeAll();
    try {
      applyChange(new RenameChange(path, newName));
    }
    finally {
      unwriteAll();
    }
  }

  public void changeROStatus(String path, boolean isReadOnly) {
    writeAll();
    try {
      applyChange(new ROStatusChange(path, isReadOnly));
    }
    finally {
      unwriteAll();
    }
  }

  public void move(String path, String newParentPath) {
    writeAll();
    try {
      applyChange(new MoveChange(path, newParentPath));
    }
    finally {
      unwriteAll();
    }
  }

  public void delete(String path) {
    writeAll();
    try {
      applyChange(new DeleteChange(path));
    }
    finally {
      unwriteAll();
    }
  }

  public Label putSystemLabel(String name, int color) {
    return applyLabel(new PutSystemLabelChange(name, color, getCurrentTimestamp()));
  }

  public Label putUserLabel(String name) {
    return applyLabel(new PutLabelChange(name, getCurrentTimestamp()));
  }

  public Label putUserLabel(String path, String name) {
    return applyLabel(new PutEntryLabelChange(path, name, getCurrentTimestamp()));
  }

  private Label applyLabel(final PutLabelChange c) {
    writeAll();
    try {
      c.applyTo(myRoot);
      addChangeToChangeList(c);
      return new Label() {
        public ByteContent getByteContent(String path) {
          return getByteContentBefore(path, c);
        }
      };
    }
    finally {
      unwriteAll();
    }
  }

  private void applyChange(Change c) {
    c.applyTo(myRoot);

    beginChangeSet();
    addChangeToChangeList(c);
    endChangeSet(null);

    myLastChange = c;
  }

  private void addChangeToChangeList(Change c) {
    myChangeList.addChange(c);
    wasModifiedAfterLastSave = true;
  }

  @TestOnly
  public ChangeList getChangeList() {
    return myChangeList;
  }

  public Change getLastChange() {
    readChangeSets();
    try {
      return myLastChange;
    }
    finally {
      unreadChangeSets();
    }
  }

  public boolean isBefore(Change before, Change after, boolean canBeEqual) {
    readChangeSets();
    try {
      return myChangeList.isBefore(before, after, canBeEqual);
    }
    finally {
      unreadChangeSets();
    }
  }

  public List<Change> getChain(Change initialChange) {
    readChangeSets();
    try {
      return myChangeList.getChain(initialChange);
    }
    finally {
      unreadChangeSets();
    }
  }

  private ByteContent getByteContentBefore(String path, Change change) {
    readAll();
    try {
      Revision revision = new RevisionAfterChange(myRoot, myRoot, myChangeList, change);
      Entry entry = revision.getEntry().findEntry(path);
      if (entry == null) return new ByteContent(false, null);
      if (entry.isDirectory()) return new ByteContent(true, null);

      return new ByteContent(false, entry.getContent().getBytesIfAvailable());
    }
    finally {
      unreadAll();
    }
  }

  public List<Revision> getRevisionsFor(String path) {
    readAll();
    try {
      return new RevisionsCollector(this, path, myRoot, myChangeList).getResult();
    }
    finally {
      unreadAll();
    }
  }

  public byte[] getByteContent(String path, FileRevisionTimestampComparator c) {
    readAll();
    try {
      return new ByteContentRetriever(this, path, c).getResult();
    }
    finally {
      unreadAll();
    }
  }

  public List<RecentChange> getRecentChanges() {
    readAll();
    try {
      List<RecentChange> result = new ArrayList<RecentChange>();

      List<Change> cc = myChangeList.getChanges();

      for (int i = 0; i < cc.size() && result.size() < 20; i++) {
        Change c = cc.get(i);
        if (c.isFileContentChange()) continue;
        if (c.isLabel()) continue;
        if (c.getName() == null) continue;

        Revision before = new RevisionBeforeChange(myRoot, myRoot, myChangeList, c);
        Revision after = new RevisionAfterChange(myRoot, myRoot, myChangeList, c);
        result.add(new RecentChange(before, after));
      }

      return result;
    }
    finally {
      unreadAll();
    }
  }

  public void acceptRead(ChangeVisitor v) throws IOException {
    readAll();
    try {
      myChangeList.getAcceptFun(myRoot, v, false).doAccept();
    }
    finally {
      unreadAll();
    }
  }

  public void acceptWrite(ChangeVisitor v) throws IOException {
    ChangeList.AcceptFun acceptFun;
    readAll();
    try {
      acceptFun = myChangeList.getAcceptFun(myRoot, v, true);
    }
    finally {
      unreadAll();
    }
    acceptFun.doAccept();
  }

  private long getCurrentTimestamp() {
    return Clock.getCurrentTimestamp();
  }

  private void writeChanges() {
    myChangeSetsWriteLock.lock();
  }

  private void unwriteChanges() {
    myChangeSetsWriteLock.unlock();
  }

  private void readChangeSets() {
    myChangeSetsReadLock.lock();
  }

  private void unreadChangeSets() {
    myChangeSetsReadLock.unlock();
  }

  private void writeEntries() {
    myEntriesWriteLock.lock();
  }

  private void unwriteEntries() {
    myEntriesWriteLock.unlock();
  }

  private void readEntries() {
    myEntriesReadLock.lock();
  }

  private void unreadEntries() {
    myEntriesReadLock.unlock();
  }

  private void readAll() {
    readEntries();
    readChangeSets();
  }

  private void unreadAll() {
    unreadEntries();
    unreadChangeSets();
  }

  private void writeAll() {
    writeEntries();
    writeChanges();
  }

  private void unwriteAll() {
    unwriteEntries();
    unwriteChanges();
  }

  public static class Memento {
    public Entry myRoot = new RootEntry();
    public int myEntryCounter = 0;
    public ChangeList myChangeList = new ChangeList();
  }
}
