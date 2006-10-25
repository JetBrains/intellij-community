package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private Snapshot mySnapshot = new Snapshot();
  private List<Change> myPendingChanges = new ArrayList<Change>();

  public boolean hasFile(String name) {
    return mySnapshot.hasFile(name);
  }

  public Revision getFileRevision(String name) {
    return mySnapshot.getFileRevision(name);
  }

  public List<Revision> getFileRevisions(String name) {
    List<Revision> result = new ArrayList<Revision>();

    Snapshot s = mySnapshot;
    Revision r = s.getFileRevision(name);

    while (s != null && r != null) {
      result.add(r);

      // todo it's possibly bug here (NullPointerException)
      s = s.revert();
      r = s.getFileRevision(r.getObjectId());
    }

    return result;
  }

  public void addFile(String name, String content) {
    myPendingChanges.add(new AddFileChange(name, content));
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
    mySnapshot = mySnapshot.apply(myPendingChanges);
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
}
