/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

public class EditorMouseEventArea {
  private final String myDebugName;

  public static final EditorMouseEventArea EDITING_AREA = new EditorMouseEventArea("EDITING_AREA");
  public static final EditorMouseEventArea LINE_NUMBERS_AREA = new EditorMouseEventArea("LINE_NUMBERS_AREA");
  public static final EditorMouseEventArea ANNOTATIONS_AREA = new EditorMouseEventArea("ANNOTATIONS_AREA");
  public static final EditorMouseEventArea LINE_MARKERS_AREA = new EditorMouseEventArea("LINE_MARKERS_AREA");
  public static final EditorMouseEventArea FOLDING_OUTLINE_AREA = new EditorMouseEventArea("FOLDING_OUTLINE_AREA");

  private EditorMouseEventArea(String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
