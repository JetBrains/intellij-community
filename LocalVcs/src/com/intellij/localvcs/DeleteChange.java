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
  public void applyTo(RootEntry root) {
    myAffectedEntry = root.getEntry(myPath);
    root.doDelete(myPath);
  }

  @Override
  public void revertOn(RootEntry root) {
    // todo maybe we should create several DeleteChanges instead of saving
    // todo previous entry?
    restoreEntryRecursively(root, myAffectedEntry, myPath);
  }

  private void restoreEntryRecursively(RootEntry root, Entry e, Path p) {
    if (e.isDirectory()) {
      root.doCreateDirectory(p, e.getObjectId());
      for (Entry child : e.getChildren()) {
        restoreEntryRecursively(root, child, p.appendedWith(child.getName()));
      }
    } else {
      root.doCreateFile(p, e.getContent(), e.getObjectId());
    }
  }
}
