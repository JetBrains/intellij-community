package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ChangeFileContentChange extends Change {
  private Path myPath;
  private String myNewContent;
  private Long myTimestamp;
  private String myOldContent;
  private Long myOldTimestamp;
  private IdPath myAffectedEntryIdPath;

  public ChangeFileContentChange(String path, String newContent, Long timestamp) {
    myPath = new Path(path);
    myNewContent = newContent;
    myTimestamp = timestamp;
  }

  public ChangeFileContentChange(Stream s) throws IOException {
    myPath = s.readPath();
    myAffectedEntryIdPath = s.readIdPath();
    myNewContent = s.readString();
    myOldContent = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writeIdPath(myAffectedEntryIdPath);
    s.writeString(myNewContent);
    s.writeString(myOldContent);
  }

  public Path getPath() {
    return myPath;
  }

  public String getNewContent() {
    return myNewContent;
  }

  public String getOldContent() {
    return myOldContent;
  }

  @Override
  public void applyTo(RootEntry root) {
    Entry affectedEntry = root.getEntry(myPath.getPath());

    myOldContent = affectedEntry.getContent();
    myOldTimestamp = affectedEntry.getTimestamp();
    myAffectedEntryIdPath = affectedEntry.getIdPath();

    root.changeFileContent(myPath.getPath(), myNewContent, myTimestamp);
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.changeFileContent(myPath.getPath(), myOldContent, myOldTimestamp);
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }

  @Override
  public Entry revertFile(Entry e) {
    if (!myAffectedEntryIdPath.getName().equals(e.getId())) return e;
    return e.withContent(myOldContent, myOldTimestamp); 
  }
}
