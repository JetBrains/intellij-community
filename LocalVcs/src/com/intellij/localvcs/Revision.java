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
}
