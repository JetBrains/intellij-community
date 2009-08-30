package com.intellij.compiler.ant;

import java.io.IOException;
import java.io.PrintWriter;

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

  public void generate(PrintWriter out) throws IOException {
    if (myComment != null) {
      out.print("<!-- ");
      out.print(myComment);
      out.print(" -->");
      if (myCommentedData != null) {
        crlf(out);
      }
    }
    if (myCommentedData != null) {
      out.print("<!-- ");
      crlf(out);
      myCommentedData.generate(out);
      crlf(out);
      out.print(" -->");
    }
  }
}
