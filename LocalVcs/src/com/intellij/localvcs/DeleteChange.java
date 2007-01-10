package com.intellij.localvcs;

import java.io.IOException;

public class DeleteChange extends Change {
  private Entry myAffectedEntry;

  public DeleteChange(String path) {
    super(path);
  }

  public DeleteChange(Stream s) throws IOException {
    super(s);
    myAffectedEntry = s.readEntry();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeEntry(myAffectedEntry);
  }

  public Entry getAffectedEntry() {
    return myAffectedEntry;
  }

  @Override
  public void applyTo(RootEntry root) {
    myAffectedEntry = root.getEntry(myPath);
    addAffectedIdPath(myAffectedEntry.getIdPath());

    root.delete(myPath);
  }

  @Override
  public void revertOn(RootEntry root) {
    // todo maybe we should create several DeleteChanges instead of saving
    // todo previous entry?
    restoreEntryRecursively(root, myAffectedEntry, myPath);
  }

  private void restoreEntryRecursively(RootEntry root, Entry e, String path) {
    if (e.isDirectory()) {
      root.createDirectory(e.getId(), path, e.getTimestamp());
      for (Entry child : e.getChildren()) {
        restoreEntryRecursively(root, child, Paths.appended(path, child.getName()));
      }
    } else {
      root.createFile(e.getId(), path, e.getContent(), e.getTimestamp());
    }
  }
}
