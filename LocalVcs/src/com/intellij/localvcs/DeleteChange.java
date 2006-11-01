package com.intellij.localvcs;

public class DeleteChange implements Change {
  private Path myPath;
  private Entry myPreviousEntry;

  public DeleteChange(Path path) {
    myPath = path;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousEntry = snapshot.getEntry(myPath);
    snapshot.doDelete(myPath);
  }

  public void revertOn(Snapshot snapshot) {
    restoreEntryRecursively(snapshot, myPreviousEntry, myPath);
  }

  private void restoreEntryRecursively(Snapshot s, Entry e, Path p) {
    if (e.isDirectory()) {
      s.doCreateDirectory(p);
      for (Entry child : e.getChildren()) {
        restoreEntryRecursively(s, child, p.appendedWith(child.getName()));
      }
    } else {
      s.doCreateFile(p, e.getContent());
    }
  }
}
