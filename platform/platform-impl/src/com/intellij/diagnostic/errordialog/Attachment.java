package com.intellij.diagnostic.errordialog;

public class Attachment {
  private final String myPath;
  private String myContent;
  private boolean myIncluded = true;

  public Attachment(String path, String content) {
    myPath = path;
    myContent = content;
  }

  public String getPath() {
    return myPath;
  }

  public String getContent() {
    return myContent;
  }

  public void setContent(String content) {
    myContent = content;
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(Boolean included) {
    myIncluded = included;
  }
}
