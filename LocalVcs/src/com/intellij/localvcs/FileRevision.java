package com.intellij.localvcs;

public class FileRevision extends Revision {
  private String myContent;

  public FileRevision(Integer objectId, FileName name, String content) {
    super(objectId, name);
    myContent = content;
  }

  @Override
  public String getContent() {
    return myContent;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o)
           && myContent.equals(((FileRevision)o).myContent);
  }
}
