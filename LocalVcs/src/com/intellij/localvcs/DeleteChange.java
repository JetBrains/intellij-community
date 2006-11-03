package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DeleteChange extends Change {
  private Path myPath;
  private Entry myAffectedEntry;

  public DeleteChange(Path path) {
    myPath = path;
  }

  public DeleteChange(DataInputStream s) throws IOException {
    myPath = new Path(s);
    if (s.readBoolean()) {
      myAffectedEntry = Entry.read(s);
    }
  }

  @Override
  public void write(DataOutputStream s) throws IOException {
    super.write(s);
    myPath.write(s);
    if (myAffectedEntry != null) {
      s.writeBoolean(true);
      myAffectedEntry.write(s);
    } else {
      s.writeBoolean(false);
    }
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
