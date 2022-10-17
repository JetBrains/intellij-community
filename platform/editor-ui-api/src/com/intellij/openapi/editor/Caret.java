// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a specific caret instance in the editor.
 * Provides methods to query and modify caret position and caret's associated selection.
 * <p>
 * Instances of this interface are supposed to be obtained from {@link CaretModel} instance, and not created explicitly.
 */
@ApiStatus.NonExtendable
public interface Caret extends UserDataHolderEx, Disposable {
  /**
   * Returns an instance of Editor, current caret belongs to.
   */
  @NotNull
  Editor getEditor();

  /**
   * Returns an instance of CaretModel, current caret is associated with.
   */
  @NotNull
  CaretModel getCaretModel();

  /**
   * Tells whether this caret is valid, i.e., recognized by the caret model currently. Caret is valid since its creation till its
   * removal from caret model.
   *
   * @see CaretModel#addCaret(VisualPosition)
   * @see CaretModel#removeCaret(Caret)
   */
  boolean isValid();

  /**
   * Moves the caret by the specified number of lines and/or columns.
   *
   * @param columnShift    the number of columns to move the caret by.
   * @param lineShift      the number of lines to move the caret by.
   * @param withSelection  if true, the caret move should extend the selection in the document.
   * @param scrollToCaret  if true, the document should be scrolled so that the caret is visible after the move.
   */
  void moveCaretRelatively(int columnShift,
                           int lineShift,
                           boolean withSelection,
                           boolean scrollToCaret);

  /**
   * Moves the caret to the specified logical position.
   * If corresponding position is in the folded region currently, the region will be expanded.
   *
   * @param pos the position to move to.
   */
  void moveToLogicalPosition(@NotNull LogicalPosition pos);

  /**
   * Moves the caret to the specified visual position.
   *
   * @param pos the position to move to.
   */
  void moveToVisualPosition(@NotNull VisualPosition pos);

  /**
   * Shorthand for calling {@link #moveToOffset(int, boolean)} with {@code 'false'} as a second argument.
   *
   * @param offset      the offset to move to
   */
  void moveToOffset(int offset);

  /**
   * Moves the caret to the specified offset in the document.
   * If corresponding position is in the folded region currently, the region will be expanded.
   *
   * @param offset                  the offset to move to.
   * @param locateBeforeSoftWrap    there is a possible case that there is a soft wrap at the given offset, hence, the same offset
   *                                corresponds to two different visual positions - just before soft wrap and just after soft wrap.
   *                                We may want to clearly indicate where to put the caret then. Given parameter allows to do that.
   *                                <b>Note:</b> it's ignored if there is no soft wrap at the given offset
   */
  void moveToOffset(int offset, boolean locateBeforeSoftWrap);

  /**
   * Tells whether caret is in consistent state currently. This might not be the case during document update, but client code can
   * observe such a state only in specific circumstances. So unless you're implementing very low-level editor logic (involving
   * {@code PrioritizedDocumentListener}), you don't need this method - you'll only see it return {@code true}.
   */
  boolean isUpToDate();

  /**
   * Returns the logical position of the caret.
   */
  @NotNull
  LogicalPosition getLogicalPosition();

  /**
   * Returns the visual position of the caret.
   */
  @NotNull
  VisualPosition getVisualPosition();

  /**
   * Returns the offset of the caret in the document. Returns 0 for a disposed (invalid) caret.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   *
   * @see #isValid()
   */
  int getOffset();

  /**
   * Returns the document offset for the start of the visual line where caret is located
   */
  int getVisualLineStart();

  /**
   * Returns the document offset that points to the first symbol shown at the next visual line after the one with caret on it
   */
  int getVisualLineEnd();

  /**
   * Returns the start offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   *
   * @see #getSelectionRange()
   */
  int getSelectionStart();

  /**
   * Returns the object that encapsulates information about visual position of selected text start if any
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   */
  @NotNull
  VisualPosition getSelectionStartPosition();

  /**
   * Returns the end offset in the document of the selected text range, or the caret
   * position if there is currently no selection.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   *
   * @see #getSelectionRange()
   */
  int getSelectionEnd();

  /**
   * Returns the object that encapsulates information about visual position of selected text end if any.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   */
  @NotNull
  VisualPosition getSelectionEndPosition();

  /**
   * Returns the text selected in the editor or null if there is currently no selection.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   */
  @Nullable
  @NlsSafe String getSelectedText();

  /**
   * Returns the offset from which the user started to extend the selection (the selection start
   * if the selection was extended in forward direction, or the selection end if it was
   * extended backward), or the caret offset if there is currently no selection.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   */
  int getLeadSelectionOffset();

  /**
   * Returns the object that encapsulates information about visual position from which the user
   * started to extend the selection if any.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   */
  @NotNull
  VisualPosition getLeadSelectionPosition();

  /**
   * Returns true if a range of text is currently selected, false otherwise.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)})
   */
  boolean hasSelection();

  /**
   * Returns current selection, or empty range at caret offset if no selection exists.
   * Must be called from inside read action (see {@link Application#runReadAction(Runnable)}).
   * This method is preferable because the most implementations are thread-safe, so the returned range is always consistent, whereas
   * the more conventional {@code TextRange.create(getSelectionStart(), getSelectionEnd())} could return inconsistent range when the selection
   * changed between {@link #getSelectionStart()} and {@link #getSelectionEnd()} calls.
   * @see #getSelectionStart()
   * @see #getSelectionEnd()
   */
  @NotNull
  default TextRange getSelectionRange() {
    return TextRange.create(getSelectionStart(), getSelectionEnd());
  }

  /**
   * Selects the specified range of text.
   * <p>
   * System selection will be updated, if such feature is supported by current editor.
   *
   * @param startOffset the start offset of the text range to select.
   * @param endOffset   the end offset of the text range to select.
   */
  void setSelection(int startOffset, int endOffset);

  /**
   * Selects the specified range of text.
   *
   * @param startOffset the start offset of the text range to select.
   * @param endOffset   the end offset of the text range to select.
   * @param updateSystemSelection whether system selection should be updated (might not have any effect if current editor doesn't support such a feature)
   */
  void setSelection(int startOffset, int endOffset, boolean updateSystemSelection);

  /**
   * Selects target range providing information about visual boundary of selection end.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   * <p/>
   * Also, in column mode this method allows to create selection spanning virtual space after the line end.
   * <p>
   * System selection will be updated, if such feature is supported by current editor.
   *
   * @param startOffset     start selection offset
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endOffset       end selection offset
   */
  void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset);

  /**
   * Selects target range based on its visual boundaries.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   * <p/>
   * Also, in column mode this method allows to create selection spanning virtual space after the line end.
   * <p>
   * System selection will be updated, if such feature is supported by current editor.
   *
   * @param startPosition   start visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param startOffset     start selection offset
   * @param endOffset       end selection offset
   */
  void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset);

  /**
   * Selects target range based on its visual boundaries.
   * <p/>
   * That is the case for soft wraps-aware processing where the whole soft wraps virtual space is matched to the same offset.
   * <p/>
   * Also, in column mode this method allows to create selection spanning virtual space after the line end.
   *
   * @param startPosition   start visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param endPosition     end visual position of the text range to select ({@code null} argument means that
   *                        no specific visual position should be used)
   * @param startOffset     start selection offset
   * @param endOffset       end selection offset
   * @param updateSystemSelection whether system selection should be updated (might not have any effect if current editor doesn't support such a feature)
   */
  void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset, boolean updateSystemSelection);

  /**
   * Removes the selection in the editor.
   */
  void removeSelection();

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
   * Clones the current caret and positions the new one right above or below the current one. If current caret has selection, corresponding
   * selection will be set for the new caret.
   *
   * @param above if {@code true}, new caret will be created at the previous line, if {@code false} - on the next line
   * @return newly created caret instance, or {@code null} if the caret cannot be created because it already exists at the new location
   * or maximum supported number of carets already exists in editor ({@link CaretModel#getMaxCaretCount()}).
   */
  @Nullable
  Caret clone(boolean above);

  /**
   * Returns {@code true} if caret is located in RTL text fragment. In that case visual column number is inversely related
   * to offset and logical column number in the vicinity of caret.
   */
  boolean isAtRtlLocation();

  /**
   * Returns {@code true} if caret is located at a boundary between different runs of bidirectional text.
   * This means that text fragments at different sides of the boundary are non-adjacent in logical order.
   * Caret can be located at any side of the boundary,
   * exact location can be determined from directionality flags of caret's logical and visual position
   * ({@link LogicalPosition#leansForward} and {@link VisualPosition#leansRight}).
   */
  boolean isAtBidiRunBoundary();

  /**
   * Returns visual attributes currently set for the caret.
   *
   * @see #setVisualAttributes(CaretVisualAttributes)
   */
  @NotNull
  CaretVisualAttributes getVisualAttributes();

  /**
   * Sets caret's current visual attributes. This can have no effect if editor doesn't support changing caret's visual appearance.
   *
   * @see #getVisualAttributes()
   */
  void setVisualAttributes(@NotNull CaretVisualAttributes attributes);
}
