package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private Snapshot mySnapshot = new Snapshot();
  private List<Modification> myPendingModifications
      = new ArrayList<Modification>();

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

      s = s.revert();
      r = s.getFileRevision(r.getObjectId());
    }

    return result;
  }

  public void addFile(String name, String content) {
    myPendingModifications.add(new AddModification(name, content));
  }

  public void changeFile(String name, String content) {
    myPendingModifications.add(new ChangeModification(name, content));
  }

  public void renameFile(String name, String newName) {
    myPendingModifications.add(new RenameModification(name, newName));
  }

  public void deleteFile(String name) {
    myPendingModifications.add(new DeleteModification(name));
  }

  public void commit() {
    // todo maby move parameter copy to cpply method? 
    mySnapshot = mySnapshot.apply(myPendingModifications);
    clearModifications();
  }

  public void revert() {
    clearModifications();

    Snapshot reverted = mySnapshot.revert();
    if (reverted == null) return;
    mySnapshot = reverted;
  }

  private void clearModifications() {
    myPendingModifications = new ArrayList<Modification>();
  }

  public boolean isClean() {
    return myPendingModifications.isEmpty();
  }
}
