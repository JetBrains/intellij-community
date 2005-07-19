/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
