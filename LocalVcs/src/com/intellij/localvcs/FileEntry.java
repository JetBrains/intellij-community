package com.intellij.localvcs;

public class FileEntry extends Entry {
  private String myContent;

  public FileEntry(Integer objectId, String name, String content) {
    super(objectId, name);
    myContent = content;
  }

  @Override
  public String getContent() {
    return myContent;
  }

  @Override
  public Entry copy() {
    return new FileEntry(myObjectId, myName, myContent);
  }

  @Override
  public Entry renamed(String newName) {
    return new FileEntry(myObjectId, newName, myContent);
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o)
           && myContent.equals(((FileEntry)o).myContent);
  }
}
