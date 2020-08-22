// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import org.jetbrains.annotations.NonNls;

public final class EditorMouseEventArea {
  private final String myDebugName;

  public static final EditorMouseEventArea EDITING_AREA = new EditorMouseEventArea("EDITING_AREA");
  public static final EditorMouseEventArea LINE_NUMBERS_AREA = new EditorMouseEventArea("LINE_NUMBERS_AREA");
  public static final EditorMouseEventArea ANNOTATIONS_AREA = new EditorMouseEventArea("ANNOTATIONS_AREA");
  public static final EditorMouseEventArea LINE_MARKERS_AREA = new EditorMouseEventArea("LINE_MARKERS_AREA");
  public static final EditorMouseEventArea FOLDING_OUTLINE_AREA = new EditorMouseEventArea("FOLDING_OUTLINE_AREA");

  private EditorMouseEventArea(@NonNls String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
