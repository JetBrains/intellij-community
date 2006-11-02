package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private Snapshot mySnapshot = new Snapshot();
  private List<Change> myPendingChanges = new ArrayList<Change>();

  public boolean hasEntry(Path path) {
    return mySnapshot.hasEntry(path);
  }

  public Entry getEntry(Path path) {
    return mySnapshot.getEntry(path);
  }

  public List<Entry> getEntryHistory(Path path) {
    //todo optimize it
    List<Entry> result = new ArrayList<Entry>();

    // todo clean up this mess
    // todo should we raise exception?
    if (!mySnapshot.hasEntry(path)) return result;

    Integer id = mySnapshot.getEntry(path).getObjectId();

    for (Snapshot snapshot : getSnapshotHistory()) {
      if (!snapshot.hasEntry(id)) break;
      result.add(snapshot.getEntry(id));
    }

    return result;
  }

  public void createDirectory(Path path) {
    myPendingChanges.add(new CreateDirectoryChange(path));
  }

  public void createFile(Path path, String content) {
    myPendingChanges.add(new CreateFileChange(path, content));
  }

  public void changeFileContent(Path path, String content) {
    myPendingChanges.add(new ChangeFileContentChange(path, content));
  }

  public void rename(Path path, String newName) {
    myPendingChanges.add(new RenameChange(path, newName));
  }

  public void delete(Path path) {
    myPendingChanges.add(new DeleteChange(path));
  }

  public void commit() {
    mySnapshot = mySnapshot.apply(new ChangeSet(myPendingChanges));
    clearPendingChanges();
  }

  public void revert() {
    clearPendingChanges();

    Snapshot reverted = mySnapshot.revert();
    if (reverted == null) return;
    mySnapshot = reverted;
  }

  private void clearPendingChanges() {
    myPendingChanges = new ArrayList<Change>();
  }

  public Boolean isClean() {
    return myPendingChanges.isEmpty();
  }

  public void putLabel(String label) {
    mySnapshot.setLabel(label);
  }

  public Snapshot getSnapshot(String label) {
    for (Snapshot s : getSnapshotHistory()) {
      if (label.equals(s.getLabel())) return s;
    }
    return null;
  }

  public List<Snapshot> getSnapshotHistory() {
    List<Snapshot> result = new ArrayList<Snapshot>();

    Snapshot s = mySnapshot;
    while (s != null) {
      result.add(s);
      s = s.revert();
    }

    // todo bad hack, maybe replace with EmptySnapshot class
    result.remove(result.size() - 1);

    return result;
  }
}
