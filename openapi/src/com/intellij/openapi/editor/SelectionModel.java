/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.SelectionListener;

public interface SelectionModel {
  int getSelectionStart();
  int getSelectionEnd();
  String getSelectedText();

  int getLeadSelectionOffset();

  boolean hasSelection();
  void setSelection(int startOffset, int endOffset);

  void removeSelection();

  void addSelectionListener(SelectionListener listener);
  void removeSelectionListener(SelectionListener listener);

  void selectLineAtCaret();
  void selectWordAtCaret(boolean honorCamelWordsSettings);

  void copySelectionToClipboard();

  void setBlockSelection(LogicalPosition blockStart, LogicalPosition blockEnd);
  void removeBlockSelection();

  boolean hasBlockSelection();
  int[] getBlockSelectionStarts();
  int[] getBlockSelectionEnds();
  LogicalPosition getBlockStart();
  LogicalPosition getBlockEnd();

  boolean isBlockSelectionGuarded();
  RangeMarker getBlockSelectionGuard();  
}
