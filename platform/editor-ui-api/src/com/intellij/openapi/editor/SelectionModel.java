// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services for selecting text in the IDE's text editor and retrieving information about the selection.
 * Most of the methods here exist for compatibility reasons, corresponding functionality is also provided by {@link CaretModel} now.
 * <p>
 * In editors supporting multiple carets, each caret has its own associated selection range. Unless mentioned explicitly, methods of this
 * interface operate on the current caret (see {@link CaretModel#runForEachCaret(CaretAction)}), or 'primary' caret if current caret
 * is not defined.
 *
 * @see Editor#getSelectionModel()
 * @see CaretModel
 */
public interface SelectionModel {

  /**
   * @return the editor this selection model belongs to
   */
  @NotNull
  Editor getEditor();

  /**
   * Returns the start offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   *
   * @return the selection start offset.
   */
  default int getSelectionStart() {
    return getEditor().getCaretModel().getCurrentCaret().getSelectionStart();
  }

  /**
   * @return    object that encapsulates information about visual position of selected text start if any
   */
  @Nullable
  default VisualPosition getSelectionStartPosition() {
    return getEditor().getCaretModel().getCurrentCaret().getSelectionStartPosition();
  }

  /**
   * Returns the end offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   *
   * @return the selection end offset.
   */
  default int getSelectionEnd() {
    return getEditor().getCaretModel().getCurrentCaret().getSelectionEnd();
  }

  /**
   * @return    object that encapsulates information about visual position of selected text end if any;
   */
  @Nullable
  default VisualPosition getSelectionEndPosition() {
    return getEditor().getCaretModel().getCurrentCaret().getSelectionEndPosition();
  }

  /**
   * Returns the text selected in the editor.
   *
   * @return the selected text, or {@code null} if there is currently no selection.
   */
  @Nullable
  default @NlsSafe String getSelectedText() {
    return getSelectedText(false);
  }

  /**
   * If {@code allCarets} is {@code true}, returns the concatenation of selections for all carets, or {@code null} if there
   * are no selections. If {@code allCarets} is {@code false}, works just like {@link #getSelectedText}.
   */
  @Nullable
  default @NlsSafe String getSelectedText(boolean allCarets) {
    if (allCarets && getEditor().getCaretModel().supportsMultipleCarets()) {
      final StringBuilder buf = new StringBuilder();
      String separator = "";
      for (Caret caret : getEditor().getCaretModel().getAllCarets()) {
        buf.append(separator);
        String caretSelectedText = caret.getSelectedText();
        if (caretSelectedText != null) {
          buf.append(caretSelectedText);
        }
        separator = "\n";
      }
      return buf.toString();
    }
    else {
      return getEditor().getCaretModel().getCurrentCaret().getSelectedText();
    }
  }

  /**
   * Returns the offset from which the user started to extend the selection (the selection start
   * if the selection was extended in forward direction, or the selection end if it was
   * extended backward).
   *
   * @return the offset from which the selection was started, or the caret offset if there is
   *         currently no selection.
   */
  default int getLeadSelectionOffset() {
    return getEditor().getCaretModel().getCurrentCaret().getLeadSelectionOffset();
  }

  /**
   * @return    object that encapsulates information about visual position from which the user started to extend the selection if any
   */
  @Nullable
  default VisualPosition getLeadSelectionPosition() {
    return getEditor().getCaretModel().getCurrentCaret().getLeadSelectionPosition();
  }

  /**
   * Checks if a range of text is currently selected.
   *
   * @return {@code true} if a range of text is selected, {@code false} otherwise.
   */
  default boolean hasSelection() {
    return hasSelection(false);
  }

  /**
   * Checks if a range of text is currently selected. If {@code anyCaret} is {@code true}, check all existing carets in
   * the document, and returns {@code true} if any of them has selection, otherwise checks only the current caret.
   *
   * @return {@code true} if a range of text is selected, {@code false} otherwise.
   */
  default boolean hasSelection(boolean anyCaret) {
    if (!anyCaret) {
      return getEditor().getCaretModel().getCurrentCaret().hasSelection();
    }
    for (Caret caret : getEditor().getCaretModel().getAllCarets()) {
      if (caret.hasSelection()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Selects the specified range of text.
   *
   * @param startOffset the start offset of the text range to select.
   * @param endOffset   the end offset of the text range to select.
   */
  default void setSelection(int startOffset, int endOffset) {
    getEditor().getCaretModel().getCurrentCaret().setSelection(startOffset, endOffset);
  }

  /**
   * Selects target range providing information about visual boundary of selection end.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   *
   * @param startOffset     start selection offset
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endOffset       end selection offset
   */
  default void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    getEditor().getCaretModel().getCurrentCaret().setSelection(startOffset, endPosition, endOffset);
  }

  /**
   * Selects target range based on its visual boundaries.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   *
   * @param startPosition   start visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param startOffset     start selection offset
   * @param endOffset       end selection offset
   */
  default void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    getEditor().getCaretModel().getCurrentCaret().setSelection(startPosition, startOffset, endPosition, endOffset);
  }


  /**
   * Removes the selection in the editor.
   */
  default void removeSelection() {
    removeSelection(false);
  }

  /**
   * Removes the selection in the editor. If {@code allCarets} is {@code true}, removes selections from all carets in the
   * editor, otherwise, does this just for the current caret.
   */
  default void removeSelection(boolean allCarets) {
    if (!allCarets) {
      getEditor().getCaretModel().getCurrentCaret().removeSelection();
    }
    else {
      for (Caret caret : getEditor().getCaretModel().getAllCarets()) {
        caret.removeSelection();
      }
    }
  }

  /**
   * Adds a listener for receiving information about selection changes.
   *
   * @param listener the listener instance.
   */
  void addSelectionListener(@NotNull SelectionListener listener);

  /**
   * Adds a listener for receiving information about selection changes, which is removed when the given disposable is disposed.
   *
   * @param listener the listener instance.
   */
  default void addSelectionListener(@NotNull SelectionListener listener, @NotNull Disposable parentDisposable) {
    addSelectionListener(listener);
    Disposer.register(parentDisposable, () -> removeSelectionListener(listener));
  }

  /**
   * Removes a listener for receiving information about selection changes.
   *
   * @param listener the listener instance.
   */
  void removeSelectionListener(@NotNull SelectionListener listener);

  /**
   * Selects the entire line of text at the caret position.
   */
  default void selectLineAtCaret() {
    getEditor().getCaretModel().getCurrentCaret().selectLineAtCaret();
  }

  /**
   * Selects the entire word at the caret position, optionally using camel-case rules to
   * determine word boundaries.
   *
   * @param honorCamelWordsSettings if true and "Use CamelHumps words" is enabled,
   *                                upper-case letters within the word are considered as
   *                                boundaries for the range of text to select.
   */
  default void selectWordAtCaret(boolean honorCamelWordsSettings) {
    getEditor().getCaretModel().getCurrentCaret().selectWordAtCaret(honorCamelWordsSettings);
  }

  /**
   * Copies the currently selected text to the clipboard.
   *
   * When multiple selections exist in the document, all of them are copied, as a single piece of text.
   */
  void copySelectionToClipboard();

  /**
   * Creates a multi-caret selection for the rectangular block of text with specified start and end positions.
   * <p>
   * If the number of carets to be created is larger than {@link CaretModel#getMaxCaretCount()}, the resulting block will be smaller than
   * requested. Editor might display a user-visible notification in such a case.
   *
   * @param blockStart the start of the rectangle to select.
   * @param blockEnd   the end of the rectangle to select.
   * @see #setSelection(int, int)
   */
  void setBlockSelection(@NotNull LogicalPosition blockStart, @NotNull LogicalPosition blockEnd);

  /**
   * Returns an array of start offsets in the document for ranges selected in the document currently. Works both for a single-caret and
   * a multiple-caret selection (for carets not having a selection, caret position is returned).
   *
   * @return an array of start offsets, array size is equal to the number of carets existing in the editor currently.
   */
  int @NotNull [] getBlockSelectionStarts();

  /**
   * Returns an array of end offsets in the document for ranges selected in the document currently. Works both for a single-caret and
   * a multiple-caret selection (for carets not having a selection, caret position is returned).
   *
   * @return an array of start offsets, array size is equal to the number of carets existing in the editor currently.
   */
  int @NotNull [] getBlockSelectionEnds();

  /**
   * Returns visual representation of selection.
   *
   * @return Selection attributes.
   */
  TextAttributes getTextAttributes();
}
