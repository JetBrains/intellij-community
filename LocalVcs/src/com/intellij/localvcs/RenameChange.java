package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class RenameChange extends Change {
  // todo remove unnecessary fields from all changes (such as path)
  private String myPath;
  private String myNewName;
  private IdPath myAffectedEntryIdPath;

  public RenameChange(String path, String newName) {
    myPath = path;
    myNewName = newName;
  }

  public RenameChange(Stream s) throws IOException {
    myPath = s.readString();
    myNewName = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeString(myPath);
    s.writeString(myNewName);
  }

  public String getPath() {
    return myPath;
  }

  public String getNewName() {
    return myNewName;
  }

  @Override
  public void applyTo(RootEntry root) {
    myAffectedEntryIdPath = root.getEntry(myPath).getIdPath();
    root.rename(myPath, myNewName);
  }

  @Override
  public void _revertOn(RootEntry root) {
    String newPath = Path.renamed(myPath, myNewName);
    String oldName = Path.getNameOf(myPath);

    root.rename(newPath, oldName);
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }

  @Override
  public Entry revertFile(Entry e) {
    if (!myAffectedEntryIdPath.getName().equals(e.getId())) return e;
    return e.renamed(myPath);
  }
}
