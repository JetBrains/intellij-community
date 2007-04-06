package com.intellij.localvcs;

import java.io.IOException;

public class CreateFileChange extends StructuralChange {
  private int myId;
  private Content myContent;
  private long myTimestamp;

  public CreateFileChange(int id, String path, Content content, long timestamp) {
    super(path);
    myId = id;
    myContent = content;
    myTimestamp = timestamp;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
    myId = s.readInteger();
    myContent = s.readContent();
    myTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myId);
    s.writeContent(myContent);
    s.writeLong(myTimestamp);
  }

  public int getId() {
    return myId;
  }

  public Content getContent() {
    return myContent;
  }

  public long getTimestamp() {
    return myTimestamp;
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
