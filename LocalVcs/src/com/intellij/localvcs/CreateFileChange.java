package com.intellij.localvcs;

import java.io.IOException;

public class CreateFileChange extends Change {
  private Integer myId;
  private Content myContent;
  private Long myTimestamp;

  public CreateFileChange(Integer id, String path, Content content, Long timestamp) {
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

  public Integer getId() {
    return myId;
  }

  public Content getContent() {
    return myContent;
  }

  public Long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public void applyTo(RootEntry root) {
    root.createFile(myId, myPath, myContent, myTimestamp);
    setAffectedIdPath(root.getEntry(myPath).getIdPath());
  }

  @Override
  public void revertOn(RootEntry root) {
    root.delete(getAffectedIdPath());
  }
}
