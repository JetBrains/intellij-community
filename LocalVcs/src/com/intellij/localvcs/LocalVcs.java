package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private Snapshot mySnapshot = new Snapshot();
  private List<Change> myPendingChanges = new ArrayList<Change>();

  public boolean hasFile(String name) {
    return mySnapshot.hasFile(name);
  }

  public boolean hasDirectory(String name) {
    return hasFile(name);
  }

  public Revision getFileRevision(String name) {
    return mySnapshot.getFileRevision(name);
  }

  public List<Revision> getFileRevisions(String name) {
    List<Revision> result = new ArrayList<Revision>();

    //todo clean up this mess
    Revision r = mySnapshot.getFileRevision(name);
    if (r == null) return result;

    for (Snapshot snapshot : getSnapshots()) {
      r = snapshot.getFileRevision(r.getObjectId());

      if (r == null) break;
      result.add(r);
    }

    return result;
  }

  public void createDirectory(String name) {
    myPendingChanges.add(new CreateFileChange(name, null));
  }

  public void createFile(String name, String content) {
    myPendingChanges.add(new CreateFileChange(name, content));
  }

  public void changeFile(String name, String content) {
    myPendingChanges.add(new ChangeContentChange(name, content));
  }

  public void renameFile(String name, String newName) {
    myPendingChanges.add(new RenameFileChange(name, newName));
  }

  public void deleteFile(String name) {
    myPendingChanges.add(new DeleteFileChange(name));
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

  public boolean isClean() {
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
