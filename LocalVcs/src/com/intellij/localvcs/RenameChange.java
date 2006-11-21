package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class RenameChange extends Change {
  // todo remove unnecessary fields from all changes (such as path)
  private Path myPath;
  private String myNewName;
  private Long myTimestamp;
  private Long myOldTimestamp;
  private IdPath myAffectedEntryIdPath;

  public RenameChange(String path, String newName, Long timestamp) {
    myPath = new Path(path);
    myNewName = newName;
    myTimestamp = timestamp;
  }

  public RenameChange(Stream s) throws IOException {
    myPath = s.readPath();
    myNewName = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writeString(myNewName);
  }

  public Path getPath() {
    return myPath;
  }

  public String getNewName() {
    return myNewName;
  }

  @Override
  public void applyTo(RootEntry root) {
    Entry affectedEntry = root.getEntry(myPath);

    myOldTimestamp = affectedEntry.getTimestamp();
    myAffectedEntryIdPath = affectedEntry.getIdPath();

    root.doRename(myPath, myNewName, myTimestamp);
  }

  @Override
  public void _revertOn(RootEntry root) {
    Path newPath = myPath.renamedWith(myNewName);
    String oldName = myPath.getName();

    root.doRename(newPath, oldName, myOldTimestamp);
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }

  @Override
  public Entry revertFile(Entry e) {
    if (!myAffectedEntryIdPath.getName().equals(e.getId())) return e;
    return e.renamed(myPath.getName(), myOldTimestamp);
  }
}
