package com.intellij.localvcs;

import java.io.IOException;

public class RenameChange extends Change {
  private String myOldName;
  private String myNewName;

  public RenameChange(String path, String newName) {
    super(path);
    myNewName = newName;
  }

  public RenameChange(Stream s) throws IOException {
    super(s);
    myOldName = s.readString();
    myNewName = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myOldName);
    s.writeString(myNewName);
  }

  public String getOldName() {
    return myOldName;
  }

  public String getNewName() {
    return myNewName;
  }

  @Override
  public void applyTo(RootEntry root) {
    Entry e = root.getEntry(myPath);

    myOldName = e.getName();
    setAffectedIdPath(e.getIdPath());

    root.rename(getAffectedIdPath(), myNewName);
  }

  @Override
  public void revertOn(RootEntry root) {
    root.rename(getAffectedIdPath(), myOldName);
  }
}
