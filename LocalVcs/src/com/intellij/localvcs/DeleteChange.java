package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class DeleteChange extends Change {
  private String myPath;
  private Entry myAffectedEntry;
  private IdPath myAffectedEntryIdPath;

  public DeleteChange(String path) {
    myPath = path;
  }

  public DeleteChange(Stream s) throws IOException {
    myPath = s.readString();
    myAffectedEntry = s.readEntry();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeString(myPath);
    s.writeEntry(myAffectedEntry);
  }

  public Entry getAffectedEntry() {
    return myAffectedEntry;
  }

  @Override
  public void applyTo(RootEntry root) {
    myAffectedEntry = root.getEntry(myPath);
    myAffectedEntryIdPath = myAffectedEntry.getIdPath();

    root.delete(myPath);
  }

  @Override
  public void _revertOn(RootEntry root) {
    // todo maybe we should create several DeleteChanges instead of saving
    // todo previous entry?
    restoreEntryRecursively(root, myAffectedEntry, myPath);
  }

  private void restoreEntryRecursively(RootEntry root, Entry e, String path) {
    if (e.isDirectory()) {
      root.createDirectory(e.getId(), path, e.getTimestamp());
      for (Entry child : e.getChildren()) {
        restoreEntryRecursively(root, child, Path.appended(path, child.getName()));
      }
    } else {
      root.createFile(e.getId(), path, e.getContent(), e.getTimestamp());
    }
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }
}
