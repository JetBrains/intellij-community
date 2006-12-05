package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CreateDirectoryChange extends Change {
  private Integer myId;
  private Long myTimestamp;

  public CreateDirectoryChange(Integer id, String path, Long timestamp) {
    super(path);
    myId = id;
    myTimestamp = timestamp;
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    super(s);
    myId = s.readInteger();
    myTimestamp = s.readLong();
  }


  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myId);
    s.writeLong(myTimestamp);
  }

  public Integer getId() {
    return myId;
  }

  public Long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public void applyTo(RootEntry root) {
    root.createDirectory(myId, myPath, myTimestamp);
    addAffectedIdPath(root.getEntry(myPath).getIdPath());
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.delete(myPath);
  }
}
