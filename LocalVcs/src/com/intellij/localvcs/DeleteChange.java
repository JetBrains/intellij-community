package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class DeleteChange extends Change {
  private Path myPath;
  private Entry myAffectedEntry;
  private IdPath myAffectedEntryIdPath;

  public DeleteChange(String path) {
    myPath = new Path(path);
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

  public Entry getAffectedEntry() {
    return myAffectedEntry;
  }

  @Override
  public void applyTo(RootEntry root) {
    myAffectedEntry = root.getEntry(myPath.getPath());
    myAffectedEntryIdPath = myAffectedEntry.getIdPath();

    root.delete(myPath.getPath());
  }

  @Override
  public void _revertOn(RootEntry root) {
    // todo maybe we should create several DeleteChanges instead of saving
    // todo previous entry?
    restoreEntryRecursively(root, myAffectedEntry, myPath);
  }

  private void restoreEntryRecursively(RootEntry root, Entry e, Path p) {
    if (e.isDirectory()) {
      root.createDirectory(e.getId(), p.getPath(), e.getTimestamp());
      for (Entry child : e.getChildren()) {
        restoreEntryRecursively(root, child, p.appendedWith(child.getName()));
      }
    } else {
      root.createFile(e.getId(), p.getPath(), e.getContent(), e.getTimestamp());
    }
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }
}
