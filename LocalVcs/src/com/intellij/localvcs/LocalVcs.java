package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private Snapshot mySnapshot = new Snapshot();
  private List<Change> myPendingChanges = new ArrayList<Change>();

  public boolean hasRevision(Filename name) {
    return mySnapshot.hasRevision(name);
  }

  public Revision getRevision(Filename name) {
    return mySnapshot.getRevision(name);
  }

  public List<Revision> getRevisions(Filename name) {
    List<Revision> result = new ArrayList<Revision>();

    //todo clean up this mess
    if (!mySnapshot.hasRevision(name)) return result;

    Integer id = mySnapshot.getRevision(name).getObjectId();

    for (Snapshot snapshot : getSnapshots()) {
      Revision r = snapshot.getRevision(id);
      if (r == null) break;

      result.add(r);
    }

    return result;
  }

  public void createDirectory(Filename name) {
    myPendingChanges.add(new CreateDirectoryChange(name));
  }

  public void createFile(Filename name, String content) {
    myPendingChanges.add(new CreateFileChange(name, content));
  }

  public void changeFile(Filename name, String content) {
    myPendingChanges.add(new ChangeContentChange(name, content));
  }

  public void renameFile(Filename name, Filename newName) {
    myPendingChanges.add(new RenameFileChange(name, newName));
  }

  public void deleteFile(Filename name) {
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
