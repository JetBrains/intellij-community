package com.intellij.localvcs;

public class Revision {
  private String myName;
  private String myContent;

  public Revision(String name, String content) {
    myName = name;
    myContent = content;
  }

  public String getName() {
    return myName;
  }

  public String getContent() {
    return myContent;
  }
}
