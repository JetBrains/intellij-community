package com.intellij.localvcs;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ChangeFileContentChange extends Change {
  private Content myNewContent;
  private Content myOldContent;
  private long myNewTimestamp;
  private long myOldTimestamp;

  public ChangeFileContentChange(String path, Content newContent, long timestamp) {
    super(path);
    myNewContent = newContent;
    myNewTimestamp = timestamp;
  }

  public ChangeFileContentChange(Stream s) throws IOException {
    super(s);
    myNewContent = s.readContent();
    myOldContent = s.readContent();
    myNewTimestamp = s.readLong();
    myOldTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeContent(myNewContent);
    s.writeContent(myOldContent);
    s.writeLong(myNewTimestamp);
    s.writeLong(myOldTimestamp);
  }

  public Content getNewContent() {
    return myNewContent;
  }

  public Content getOldContent() {
    return myOldContent;
  }

  public long getNewTimestamp() {
    return myNewTimestamp;
  }

  public long getOldTimestamp() {
    return myOldTimestamp;
  }

  @Override
  protected IdPath doApplyTo(RootEntry root) {
    Entry affectedEntry = root.getEntry(myPath);

    myOldContent = affectedEntry.getContent();
    myOldTimestamp = affectedEntry.getTimestamp();

    IdPath idPath = affectedEntry.getIdPath();
    root.changeFileContent(idPath, myNewContent, myNewTimestamp);
    return idPath;
  }

  @Override
  public void revertOn(RootEntry root) {
    root.changeFileContent(myAffectedIdPath, myOldContent, myOldTimestamp);
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.singletonList(myOldContent);
  }
}
