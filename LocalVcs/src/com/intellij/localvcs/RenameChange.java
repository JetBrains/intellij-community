package com.intellij.localvcs;

import java.io.IOException;

public class RenameChange extends StructuralChange {
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
  protected IdPath doApplyTo(RootEntry root) {
    Entry e = root.getEntry(myPath);

    // todo one more hack to support roots...
    // todo i defitilety have to do something with it...
    myOldName = Paths.getNameOf(e.getName());

    IdPath idPath = e.getIdPath();
    root.rename(idPath, myNewName);
    return idPath;
  }

  @Override
  public void revertOn(RootEntry root) {
    root.rename(myAffectedIdPath, myOldName);
  }
}
