package com.intellij.dupLocator.treeHash;

import org.jetbrains.annotations.Nls;

public class GroupNodeDescription {
  private final int myFilesCount;
  private final @Nls String myTitle;
  private final @Nls String myComment;


  public GroupNodeDescription(final int filesCount, final @Nls String title, final @Nls String comment) {
    myFilesCount = filesCount;
    myTitle = title;
    myComment = comment;
  }


  public int getFilesCount() {
    return myFilesCount;
  }

  public @Nls String getTitle() {
    return myTitle;
  }

  public @Nls String getComment() {
    return myComment;
  }
}
