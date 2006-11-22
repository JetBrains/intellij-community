package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CreateDirectoryChange extends Change {
  private Integer myId;
  private Path myPath;
  private Long myTimestamp;
  
  private IdPath myAffectedEntryIdPath;

  public CreateDirectoryChange(Integer id, String path, Long timestamp) {
    myId = id;
    myPath = new Path(path);
    myTimestamp = timestamp;
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    myPath = s.readPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
  }

  public Path getPath() {
    return myPath;
  }

  @Override
  public void applyTo(RootEntry root) {
    root.createDirectory(myId, myPath.getPath(), myTimestamp);
    myAffectedEntryIdPath = root.getEntry(myPath.getPath()).getIdPath();
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.delete(myPath.getPath());
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }
}
