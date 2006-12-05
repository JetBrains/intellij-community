package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CreateFileChange extends Change {
  // todo test storing of all the changes and test it once and for all times 8))
  private Integer myId;
  private String myContent;
  private Long myTimestamp;

  public CreateFileChange(Integer id, String path, String content, Long timestamp) {
    super(path);
    myId = id;
    myContent = content;
    myTimestamp = timestamp;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
    myId = s.readInteger();
    myContent = s.readString();
    myTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myId);
    s.writeString(myContent);
    s.writeLong(myTimestamp);
  }

  public Integer getId() {
    return myId;
  }

  public String getContent() {
    return myContent;
  }

  public Long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public void applyTo(RootEntry root) {
    root.createFile(myId, myPath, myContent, myTimestamp);
    addAffectedIdPath(root.getEntry(myPath).getIdPath());
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.delete(myPath);
  }
}
