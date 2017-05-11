package com.intellij.dupLocator.treeHash;

public class GroupNodeDescription {
  private final int myFilesCount;
  private final String myTitle;
  private final String myComment;


  public GroupNodeDescription(final int filesCount, final String title, final String comment) {
    myFilesCount = filesCount;
    myTitle = title;
    myComment = comment;
  }


  public int getFilesCount() {
    return myFilesCount;
  }

  public String getTitle() {
    return myTitle;
  }

  public String getComment() {
    return myComment;
  }
}
