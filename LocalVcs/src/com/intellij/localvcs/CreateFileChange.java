package com.intellij.localvcs;

import java.io.IOException;

public class CreateFileChange extends StructuralChange {
  private int myId; // transient
  private Content myContent; // transient
  private long myTimestamp; // transient

  public CreateFileChange(int id, String path, Content content, long timestamp) {
    super(path);
    myId = id;
    myContent = content;
    myTimestamp = timestamp;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(RootEntry root) {
    return root.createFile(myId, myPath, myContent, myTimestamp).getIdPath();
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
