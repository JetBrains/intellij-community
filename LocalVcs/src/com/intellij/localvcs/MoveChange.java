package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MoveChange extends Change {
  private String myPath;
  private String myNewParentPath;
  private Long myTimestamp;
  private Long myOldTimestamp;
  private IdPath myFromIdPath;
  private IdPath myToIdPath;

  public MoveChange(String path, String newParentPath, Long timestamp) {
    myPath = path;
    myNewParentPath = newParentPath;
    myTimestamp = timestamp;
  }

  public MoveChange(Stream s) throws IOException {
    myPath = s.readString();
    myNewParentPath = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeString(myPath);
    s.writeString(myNewParentPath);
  }

  public String getPath() {
    return myPath;
  }

  public String getNewParentPath() {
    return myNewParentPath;
  }

  @Override
  public void applyTo(RootEntry root) {
    Entry e = root.getEntry(myPath);

    myOldTimestamp = e.getTimestamp();
    myFromIdPath = e.getIdPath();
    root.move(myPath, myNewParentPath, myTimestamp);
    myToIdPath = root.getEntry(getNewPath()).getIdPath();
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.move(getNewPath(), new Path(myPath).getParent().getPath(), myOldTimestamp);
  }

  private String getNewPath() {
    return new Path(myNewParentPath).appendedWith(new Path(myPath).getName()).getPath();
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myFromIdPath, myToIdPath);
  }
}
