package com.intellij.localvcs;

import java.io.IOException;

public class FileEntry extends Entry {
  // todo change String to ByteArray or something else
  private String myContent;

  public FileEntry(Integer objectId, String name, String content) {
    super(objectId, name);
    myContent = content;
  }

  public FileEntry(Stream s) throws IOException {
    super(s);
    myContent = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myContent);
  }

  @Override
  public String getContent() {
    return myContent;
  }

  @Override
  public Entry copy() {
    return new FileEntry(myObjectId, myName, myContent);
  }

  public Difference getDifferenceWith(FileEntry e) {
    if (myName.equals(e.myName)
        && myContent.equals(e.myContent)) return null;

    return new ModifiedDifference(this, e);
  }
}
