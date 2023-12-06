// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable;

/**
 * @author Alexander Lobas
 */
public final class InplaceContext {
  private char myStartChar;

  public InplaceContext() {
    this((char)0);
  }

  public InplaceContext(char startChar) {
    myStartChar = startChar;
  }

  public boolean isStartChar() {
    return myStartChar != 0;
  }

  public String getText(String text) {
    if (text == null) {
      text = "";
    }

    text += myStartChar;
    myStartChar = 0;
    return text;
  }
}