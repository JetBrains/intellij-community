package com.intellij.localvcs;

import java.io.IOException;

public class CreateDirectoryChange extends Change {
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
  protected void doApplyTo(RootEntry root) {
    Entry e = root.createDirectory(myId, myPath);
    setAffectedIdPath(e.getIdPath());
  }

  @Override
  public void revertOn(RootEntry root) {
    root.delete(getAffectedIdPath());
  }
}
