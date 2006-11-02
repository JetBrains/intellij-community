package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DeleteChange extends Change {
  private Path myPath;
  private Entry myPreviousEntry;

  public DeleteChange(Path path) {
    myPath = path;
  }

  public DeleteChange(DataInputStream s) throws IOException {
    myPath = new Path(s);
  }

  @Override
  public void write(DataOutputStream s) throws IOException {
    super.write(s);
    myPath.write(s);
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    myPreviousEntry = snapshot.getEntry(myPath);
    snapshot.doDelete(myPath);
  }

  @Override
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
