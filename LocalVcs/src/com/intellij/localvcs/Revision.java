package com.intellij.localvcs;

public class Revision {
  private Integer myObjectId;
  private String myName;
  private String myContent;

  public Revision(Integer objectId, String name, String content) {
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
    Revision r = (Revision)o;
    return myObjectId.equals(r.myObjectId)
           && myName.equals(r.myName)
           && myContent.equals(r.myContent);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
