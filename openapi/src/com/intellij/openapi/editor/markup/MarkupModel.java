/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.UserDataHolder;

public interface MarkupModel extends UserDataHolder {
  Document getDocument();

  RangeHighlighter addRangeHighlighter(int startOffset, int endOffset, int layer, TextAttributes textAttributes, HighlighterTargetArea targetArea);
  RangeHighlighter addLineHighlighter(int line, int layer, TextAttributes textAttributes);

  void removeHighlighter(RangeHighlighter rangeHighlighter);
  void removeAllHighlighters();

  RangeHighlighter[] getAllHighlighters();
}
