/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.colors;

public final class EditorFontType {
  private String myDebugName;

  private EditorFontType(String debugName) {
    myDebugName = debugName;
  }

  public static final EditorFontType PLAIN = new EditorFontType("PLAIN");
  public static final EditorFontType BOLD = new EditorFontType("BOLD");
  public static final EditorFontType ITALIC = new EditorFontType("ITALIC");
  public static final EditorFontType BOLD_ITALIC = new EditorFontType("BOLD_ITALIC");

  public String toString() {
    return myDebugName;
  }
}
