package com.intellij.compiler.ant;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Comment extends Generator{
  private final String myComment;
  private final Generator myCommentedData;

  public Comment(String comment) {
    this(comment, null);
  }

  public Comment(Generator commentedData) {
    this(null, commentedData);
  }

  public Comment(String comment, Generator commentedData) {
    myComment = comment;
    myCommentedData = commentedData;
  }

  public void generate(DataOutput out) throws IOException {
    if (myComment != null) {
      out.writeBytes("<!-- ");
      out.writeBytes(myComment);
      out.writeBytes(" -->");
      if (myCommentedData != null) {
        crlf(out);
      }
    }
    if (myCommentedData != null) {
      out.writeBytes("<!-- ");
      crlf(out);
      myCommentedData.generate(out);
      crlf(out);
      out.writeBytes(" -->");
    }
  }
}
