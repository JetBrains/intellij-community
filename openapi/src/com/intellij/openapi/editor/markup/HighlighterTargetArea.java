/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

public class HighlighterTargetArea {
  public static final HighlighterTargetArea EXACT_RANGE = new HighlighterTargetArea("EXACT_RANGE");
  public static final HighlighterTargetArea LINES_IN_RANGE = new HighlighterTargetArea("LINES_IN_RANGE");
  private String myDebugName;

  private HighlighterTargetArea(String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
