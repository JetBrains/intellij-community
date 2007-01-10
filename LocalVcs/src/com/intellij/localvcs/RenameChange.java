package com.intellij.localvcs;

import java.io.IOException;

public class RenameChange extends Change {
  private String myNewName;

  public RenameChange(String path, String newName) {
    super(path);
    myNewName = newName;
  }

  public RenameChange(Stream s) throws IOException {
    super(s);
    myNewName = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myNewName);
  }

  public String getNewName() {
    return myNewName;
  }

  @Override
  public void applyTo(RootEntry root) {
    addAffectedIdPath(root.getEntry(myPath).getIdPath());
    root.rename(myPath, myNewName);
  }

  @Override
  public void revertOn(RootEntry root) {
    String newPath = Paths.renamed(myPath, myNewName);
    String oldName = Paths.getNameOf(myPath);

    root.rename(newPath, oldName);
  }
}
