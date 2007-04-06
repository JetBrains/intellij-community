package com.intellij.localvcs;

import java.io.IOException;

public class CreateDirectoryChange extends StructuralChange {
  private int myId;

  public CreateDirectoryChange(int id, String path) {
    super(path);
    myId = id;
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    super(s);
    myId = s.readInteger();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myId);
  }

  public int getId() {
    return myId;
  }

  @Override
  protected IdPath doApplyTo(RootEntry root) {
    return root.createDirectory(myId, myPath).getIdPath();
  }

  @Override
  public void revertOn(RootEntry root) {
    root.delete(myAffectedIdPath);
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return e.getId() == myAffectedIdPath.getId();
  }
}
