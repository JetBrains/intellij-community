package com.intellij.localvcs;

public class FileRevision extends Revision {
  private Integer myObjectId;
  private String myName;
  private String myContent;

  public FileRevision(Integer objectId, String name, String content) {
    myObjectId = objectId;
    myName = name;
    myContent = content;
  }

  public Integer getObjectId() {
    return myObjectId;
  }

  public String getName() {
    return myName;
  }

  public String getContent() {
    return myContent;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !o.getClass().equals(getClass())) return false;
    FileRevision r = (FileRevision)o;
    return myObjectId.equals(r.myObjectId)
           && myName.equals(r.myName)
           && myContent.equals(r.myContent);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
