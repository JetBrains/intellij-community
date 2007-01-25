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
    setAffectedIdPath(myAffectedEntry.getIdPath());

    root.delete(getAffectedIdPath());
  }

  @Override
  public void revertOn(RootEntry root) {
    restoreEntryRecursively(root, myAffectedEntry, getAffectedIdPath().getParent());
  }

  private void restoreEntryRecursively(RootEntry root, Entry e, IdPath parentPath) {
    if (e.isDirectory()) {
      root.createDirectory(e.getId(), parentPath, e.getName(), e.getTimestamp());
      for (Entry child : e.getChildren()) {
        parentPath = parentPath == null ? e.getIdPath() : parentPath.appendedWith(e.getId());
        restoreEntryRecursively(root, child, parentPath);
      }
    }
    else {
      root.createFile(e.getId(), parentPath, e.getName(), e.getContent(), e.getTimestamp());
    }
  }
}
