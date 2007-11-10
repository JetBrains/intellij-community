/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 20:23:22
 */
package com.intellij.openapi.diff.impl.patch;

public class PatchSyntaxException extends Exception {
  private int myLine;

  public PatchSyntaxException(int line, String message) {
    super(message);
    myLine = line;
  }

  public int getLine() {
    return myLine;
  }
}