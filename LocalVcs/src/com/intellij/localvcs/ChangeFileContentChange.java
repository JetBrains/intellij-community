package com.intellij.localvcs;

import java.io.IOException;

public class ChangeFileContentChange extends Change {
  private String myNewContent;
  private String myOldContent;
  private Long myNewTimestamp;
  private Long myOldTimestamp;

  public ChangeFileContentChange(String path, String newContent, Long timestamp) {
    super(path);
    myNewContent = newContent;
    myNewTimestamp = timestamp;
  }

  public ChangeFileContentChange(Stream s) throws IOException {
    super(s);
    myNewContent = s.readString();
    myOldContent = s.readString();
    myNewTimestamp = s.readLong();
    myOldTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myNewContent);
    s.writeString(myOldContent);
    s.writeLong(myNewTimestamp);
    s.writeLong(myOldTimestamp);
  }

  public String getNewContent() {
    return myNewContent;
  }

  public String getOldContent() {
    return myOldContent;
  }


  public Long getNewTimestamp() {
    return myNewTimestamp;
  }

  public Long getOldTimestamp() {
    return myOldTimestamp;
  }

  @Override
  public void applyTo(RootEntry root) {
    Entry affectedEntry = root.getEntry(myPath);

    myOldContent = affectedEntry.getContent();
    myOldTimestamp = affectedEntry.getTimestamp();
    addAffectedIdPath(affectedEntry.getIdPath());

    root.changeFileContent(myPath, myNewContent, myNewTimestamp);
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.changeFileContent(myPath, myOldContent, myOldTimestamp);
  }

  @Override
  public Entry revertFile(Entry e) {
    if (!isFor(e)) return e;
    return e.withContent(myOldContent, myOldTimestamp); 
  }
}
