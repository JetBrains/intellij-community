/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services for selecting text in the IDEA text editor and retrieving information
 * about the selection. The editor supports two modes of selection: range selection (where
 * a sequential range of text from one offset to another is selected) and block selection
 * (where a rectangular block of text is selected). Different functions exist for handling
 * these two types of selection, but only one type of selection can exist in a document at
 * any given time.
 *
 * @see Editor#getSelectionModel()
 */
public interface SelectionModel {
  /**
   * Returns the start offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   *
   * @return the selection start offset.
   */
  int getSelectionStart();

  /**
   * @return    object that encapsulates information about visual position of selected text start if any
   */
  @Nullable
  VisualPosition getSelectionStartPosition();

  /**
   * Returns the end offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   *
   * @return the selection end offset.
   */
  int getSelectionEnd();

  /**
   * @return    object that encapsulates information about visual position of selected text end if any;
   */
  @Nullable
  VisualPosition getSelectionEndPosition();

  /**
   * Returns the text selected in the editor (or the concatenation of text ranges selected
   * in each line, if block selection mode is used).
   *
   * @return the selected text, or null if there is currently no selection.
   */
  @Nullable
  String getSelectedText();

  /**
   * Returns the offset from which the user started to extend the selection (the selection start
   * if the selection was extended in forward direction, or the selection end if it was
   * extended backward).
   *
   * @return the offset from which the selection was started, or the caret offset if there is
   *         currently no selection.
   */
  int getLeadSelectionOffset();

  /**
   * @return    object that encapsulates information about visual position from which the user started to extend the selection if any
   */
  @Nullable
  VisualPosition getLeadSelectionPosition();

  /**
   * Checks if a range of text is currently selected in regular (non-block) selection mode.
   *
   * @return true if a range of text is selected, false otherwise.
   * @see #hasBlockSelection()
   */
  boolean hasSelection();

  /**
   * Selects the specified range of text in regular (non-block) selection mode.
   *
   * @param startOffset the start offset of the text range to select.
   * @param endOffset   the end offset of the text range to select.
   * @see #setBlockSelection(LogicalPosition, LogicalPosition)
   */
  void setSelection(int startOffset, int endOffset);

  /**
   * Selects target range providing information about visual boundary of selection end.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   *
   * @param startOffset     start selection offset
   * @param endPosition     end visual position of the text range to select (<code>null</code> argument means that
   *                        no specific visual position should be used)
   * @param endOffset       end selection offset
   */
  void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset);

  /**
   * Selects target range based on its visual boundaries.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   *
   * @param startPosition   start visual position of the text range to select (<code>null</code> argument means that
   *                        no specific visual position should be used)
   * @param endPosition     end visual position of the text range to select (<code>null</code> argument means that
   *                        no specific visual position should be used)
   * @param startOffset     start selection offset
   * @param endOffset       end selection offset
   */
  void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset);

  /**
   * Removes the selection in the editor.
   */
  void removeSelection();

  /**
   * Adds a listener for receiving information about selection changes.
   *
   * @param listener the listener instance.
   */
  void addSelectionListener(SelectionListener listener);

  /**
   * Removes a listener for receiving information about selection changes.
   *
   * @param listener the listener instance.
   */
  void removeSelectionListener(SelectionListener listener);

  /**
   * Selects the entire line of text at the caret position.
   */
  void selectLineAtCaret();

  /**
   * Selects the entire word at the caret position, optionally using camel-case rules to
   * determine word boundaries.
   *
   * @param honorCamelWordsSettings if true and "Use CamelHumps words" is enabled,
   *                                upper-case letters within the word are considered as
   *                                boundaries for the range of text to select.
   */
  void selectWordAtCaret(boolean honorCamelWordsSettings);

  /**
   * Copies the currently selected text to the clipboard.
   */
  void copySelectionToClipboard();

  /**
   * Selects the specified rectangle of text in block selection mode.
   *
   * @param blockStart the start of the rectangle to select.
   * @param blockEnd   the end of the rectangle to select.
   * @see #setSelection(int, int)
   */
  void setBlockSelection(LogicalPosition blockStart, LogicalPosition blockEnd);

  /**
   * Removes the block selection from the document.
   *
   * @see #removeSelection()
   */
  void removeBlockSelection();

  /**
   * Checks if a rectangular block of text is currently selected in the document.
   *
   * @return true if a block selection currently exists, false otherwise.
   * @see #hasSelection()
   */
  boolean hasBlockSelection();

  /**
   * Returns an array of start offsets in the document for line parts selected
   * in block selection mode. The size of the returned array is equal to the height
   * of the selected text block.
   *
   * @return the array of start offsets, or an array of 1 element if a range selection
   * currently exists, or an empty array if no selection exists.
   */
  @NotNull
  int[] getBlockSelectionStarts();

  /**
   * Returns an array of end offsets in the document for line parts selected
   * in block selection mode. The size of the returned array is equal to the height
   * of the selected text block.
   *
   * @return the array of end offsets, or an array of 1 element if a range selection
   * currently exists, or an empty array if no selection exists.
   */
  @NotNull
  int[] getBlockSelectionEnds();

  /**
   * Returns the start position of the current block selection.
   *
   * @return the block selection start, or null if no block selection currently exists.
   */
  @Nullable
  LogicalPosition getBlockStart();

  /**
   * Returns the end position of the current block selection.
   *
   * @return the block selection end, or null if no block selection currently exists.
   */
  @Nullable
  LogicalPosition getBlockEnd();

  /**
   * Checks if any read-only markers intersect the current block selection.
   *
   * @return true if part of the block selection is read-only, false otherwise.
   * @see Document#createGuardedBlock(int, int)
   */
  boolean isBlockSelectionGuarded();

  /**
   * Returns a read-only marker intersecting the current block selection.
   *
   * @return the marker instance, or null if there is no block selection or no read-only marker
   * intersects it.
   */
  @Nullable
  RangeMarker getBlockSelectionGuard();

  /**
   * Returns visual representation of selection.
   *
   * @return Selection attributes.
   */
  TextAttributes getTextAttributes();
}
