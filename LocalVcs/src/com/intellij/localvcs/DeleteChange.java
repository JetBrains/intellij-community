package com.intellij.localvcs;

import java.io.IOException;

public class DeleteChange extends Change {
  private Path myPath;
  private Entry myAffectedEntry;

  public DeleteChange(Path path) {
    myPath = path;
  }

  public DeleteChange(Stream s) throws IOException {
    myPath = s.readPath();
    myAffectedEntry = s.readEntry();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writeEntry(myAffectedEntry);
  }

  public Path getPath() {
    return myPath;
  }

  public Entry getAffectedEntry() {
    return myAffectedEntry;
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    myAffectedEntry = snapshot.getEntry(myPath);
    snapshot.doDelete(myPath);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    // todo maybe we should create several DeleteChanges instead of saving
    // previous entry?
    restoreEntryRecursively(snapshot, myAffectedEntry, myPath);
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
