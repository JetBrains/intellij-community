/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

public interface HighlighterLayer {
  int CARET_ROW = 1000;
  int SYNTAX = 2000;
  int ADDITIONAL_SYNTAX = 3000;
  int GUARDED_BLOCKS = 3500;
  int WARNING = 4000;
  int ERROR = 5000;
  int SELECTION = 6000;

  int FIRST = CARET_ROW;
  int LAST = SELECTION;
}
