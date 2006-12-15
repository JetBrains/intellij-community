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
  public void _revertOn(RootEntry root) {
    String newPath = Path.renamed(myPath, myNewName);
    String oldName = Path.getNameOf(myPath);

    root.rename(newPath, oldName);
  }

  @Override
  public void revertFile(Entry e) {
    if (!isFor(e)) return;
    e.changeName(myPath);
  }
}
