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

  public List<Entry> getEntries(Path path) {
    List<Entry> result = new ArrayList<Entry>();

    //todo clean up this mess
    if (!mySnapshot.hasEntry(path)) return result;

    Integer id = mySnapshot.getEntry(path).getObjectId();

    for (Snapshot snapshot : getSnapshots()) {
      Entry e = snapshot.getEntry(id);
      if (e == null) break;

      result.add(e);
    }

    return result;
  }

  public void createDirectory(Path path) {
    myPendingChanges.add(new CreateDirectoryChange(path));
  }

  public void createFile(Path path, String content) {
    myPendingChanges.add(new CreateFileChange(path, content));
  }

  public void changeFile(Path path, String content) {
    myPendingChanges.add(new ChangeContentChange(path, content));
  }

  public void renameFile(Path path, String newName) {
    myPendingChanges.add(new RenameFileChange(path, newName));
  }

  public void deleteFile(Path path) {
    myPendingChanges.add(new DeleteFileChange(path));
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
    for (Snapshot s : getSnapshots()) {
      if (label.equals(s.getLabel())) return s;
    }
    return null;
  }

  public List<Snapshot> getSnapshots() {
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
