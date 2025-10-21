// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * Represents an instance of a text editor.
 * <p>
 * The data source of an editor consists of:
 * <ul>
 * <li>{@link #getDocument()}
 * <li>{@link #getProject()}
 * <li>{@link #getVirtualFile()}
 * <li>{@link #getEditorKind()}
 * </ul>
 * The state of an editor consists of:
 * <ul>
 * <li>{@link #getSettings()}
 * <li>{@link #isViewer()}
 * <li>{@link #isInsertMode()}
 * <li>{@link #isColumnMode()}
 * <li>{@link #isOneLineMode()}
 * <li>{@link #isDisposed()}
 * <li>{@link #getCaretModel()}
 * <li>{@link #getSelectionModel()}
 * </ul>
 * The appearance of an editor is determined by:
 * <ul>
 * <li>{@link #getColorsScheme()}
 * <li>{@link #getScrollingModel()}
 * <li>{@link #getSoftWrapModel()}
 * <li>{@link #getFoldingModel()}
 * <li>{@link #getHighlighter()}
 * <li>{@link #getMarkupModel()}
 * <li>{@link #getIndentsModel()}
 * <li>{@link #getInlayModel()}
 * </ul>
 * The visual parts of an editor are:
 * <ul>
 * <li>{@link #getComponent()}
 * <li>{@link #getContentComponent()}
 * <li>{@link #setBorder(Border)}
 * <li>{@link #getInsets()}
 * <li>{@link #setHeaderComponent(JComponent)}
 * <li>{@link #hasHeaderComponent()}
 * <li>{@link #getHeaderComponent()}
 * <li>{@link #getGutter()}
 * <li>{@link #getLineHeight()}
 * <li>{@link #getAscent()}
 * </ul>
 * The mouse interaction of an editor is controlled with:
 * <ul>
 * <li>{@link #addEditorMouseListener(EditorMouseListener)}
 * <li>{@link #addEditorMouseListener(EditorMouseListener, Disposable)}
 * <li>{@link #removeEditorMouseListener(EditorMouseListener)}
 * <li>{@link #addEditorMouseMotionListener(EditorMouseMotionListener)}
 * <li>{@link #addEditorMouseMotionListener(EditorMouseMotionListener, Disposable)}
 * <li>{@link #removeEditorMouseMotionListener(EditorMouseMotionListener)}
 * <li>{@link #getMouseEventArea(MouseEvent)}
 * </ul>
 * The remaining methods deal with conversion between offsets,
 * logical positions, visual positions, and screen coordinates.
 *
 * @see EditorFactory#createEditor(Document)
 * @see EditorFactory#createViewer(Document)
 */
public interface Editor extends UserDataHolder {
  Editor[] EMPTY_ARRAY = new Editor[0];

  /** Returns the document edited or viewed in the editor. */
  @NotNull Document getDocument();

  /** Returns whether the editor operates in viewer mode, with all modification actions disabled. */
  boolean isViewer();

  /**
   * Returns the component for the entire editor, including the scrollbars, error stripe, gutter
   * and other decorations. The component can be used, for example, for converting logical to
   * screen coordinates.
   */
  @NotNull JComponent getComponent();

  /**
   * Returns the component for the content area of the editor (the area displaying the document text).
   * The component can be used, for example, for converting logical to screen coordinates.
   * The instance is implementing {@link DataProvider}.
   */
  @NotNull JComponent getContentComponent();

  void setBorder(@Nullable Border border);

  Insets getInsets();

  /**
   * Returns the selection model for the editor, which can be used to select ranges of text in
   * the document and retrieve information about the selection.
   * <p>
   * To query or change selections for specific carets, see {@link #getCaretModel()}.
   */
  @NotNull SelectionModel getSelectionModel();

  /**
   * Returns the markup model for the editor. This model contains editor-specific highlighters
   * (for example, highlighters added by "Highlight usages in file"), which are painted in addition
   * to the highlighters contained in the markup model for the document.
   * <p>
   * See also {@link com.intellij.openapi.editor.impl.DocumentMarkupModel#forDocument(Document, Project, boolean)}
   * {@link com.intellij.openapi.editor.ex.EditorEx#getFilteredDocumentMarkupModel()}.
   */
  @NotNull MarkupModel getMarkupModel();

  /**
   * Returns the folding model for the document, which can be used to add, remove, expand
   * or collapse folded regions in the document.
   */
  @NotNull FoldingModel getFoldingModel();

  /**
   * Returns the scrolling model for the document, which can be used to scroll the document
   * and retrieve information about the current position of the scrollbars.
   */
  @NotNull ScrollingModel getScrollingModel();

  /**
   * Returns the caret model for the document, which can be used to add and remove carets to the editor, as well as to query and update
   * carets' and corresponding selections' positions.
   */
  @NotNull CaretModel getCaretModel();

  /**
   * Returns the soft wrap model for the document, which can be used to get information about soft wraps registered
   * for the editor document at the moment and provides basic management functions for them.
   */
  @NotNull SoftWrapModel getSoftWrapModel();

  /**
   * Returns the editor settings for this editor instance.
   * Changes to these settings affect only the current editor instance.
   */
  @NotNull EditorSettings getSettings();

  /**
   * Returns the editor color scheme for this editor instance.
   * Changes to the scheme affect only the current editor instance.
   */
  @NotNull EditorColorsScheme getColorsScheme();

  /** Returns the height of a single line of text in the current editor font, in pixels. */
  int getLineHeight();

  /**
   * Maps a logical position in the editor to pixel coordinates,
   * relative to the top left corner of the {@linkplain #getContentComponent() content component}.
   */
  @NotNull Point logicalPositionToXY(@NotNull LogicalPosition pos);

  /** Maps a logical position in the editor to the offset in the document. */
  int logicalPositionToOffset(@NotNull LogicalPosition pos);

  /**
   * Maps a logical position in the editor (the line and column ignoring folding) to
   * a visual position (with folded lines and columns not included in the line and column count).
   */
  @NotNull VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos);

  /**
   * Maps a visual position in the editor to pixel coordinates,
   * relative to the top left corner of the {@linkplain #getContentComponent() content component}.
   */
  @NotNull Point visualPositionToXY(@NotNull VisualPosition visible);

  /** Same as {@link #visualPositionToXY(VisualPosition)}, but returns a potentially more precise result. */
  @NotNull Point2D visualPositionToPoint2D(@NotNull VisualPosition pos);

  /**
   * Maps a visual position in the editor (with folded lines and columns not included in the line and column count) to
   * a logical position (the line and column ignoring folding).
   */
  @NotNull LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos);

  default int visualPositionToOffset(@NotNull VisualPosition pos) {
    return logicalPositionToOffset(visualToLogicalPosition(pos));
  }

  /**
   * Maps an offset in the document to a logical position.
   * <p>
   * It's assumed that the original position is associated with the character immediately preceding the given offset,
   * so the resulting logical position will have its {@link LogicalPosition#leansForward leansForward} value set to {@code false}.
   *
   * @param offset the offset in the document. Negative values are clamped to zero; values bigger than text length are clamped
   *               to the text length
   */
  @NotNull LogicalPosition offsetToLogicalPosition(int offset);

  /**
   * Maps an offset in the document to the corresponding visual position.
   * <p>
   * It's assumed that the original position is associated with the character immediately preceding the given offset,
   * the {@link VisualPosition#leansRight leansRight} value for the visual position will be determined accordingly.
   * <p>
   * If there's a soft wrap at the given offset, the visual position of the line following the wrap will be returned.
   *
   * @param offset the offset in the document.
   */
  @NotNull VisualPosition offsetToVisualPosition(int offset);

  /**
   * Maps an offset in the document to the corresponding visual position.
   *
   * @param offset         the offset in the document.
   * @param leanForward    if {@code true}, the original position is associated with the character after the given offset,
   *                       if {@code false} - with the character before given offset.
   *                       This can make a difference in bidirectional text (see {@link LogicalPosition}, {@link VisualPosition})
   * @param beforeSoftWrap if {@code true}, the visual position at the line preceding the wrap will be returned,
   *                       otherwise - visual position at line following the wrap.
   */
  @NotNull VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap);

  /**
   * Maps an offset in the document to a visual line in the editor.
   *
   * @param offset         the offset in the document.
   * @param beforeSoftWrap flag to resolve the ambiguity if there's a soft wrap at target offset.
   *                       If {@code true}, the visual line ending in the soft wrap will be returned,
   *                       otherwise the visual line following the wrap.
   */
  default int offsetToVisualLine(int offset, boolean beforeSoftWrap) {
    return offsetToVisualPosition(offset, false /* doesn't matter if only visual line is needed */, beforeSoftWrap).line;
  }

  /**
   * Maps the pixel coordinates in the editor to a logical position.
   *
   * @param p the coordinates relative to the top left corner of the {@link #getContentComponent() content component}.
   */
  @NotNull LogicalPosition xyToLogicalPosition(@NotNull Point p);

  /**
   * Maps the pixel coordinates in the editor to a visual position.
   *
   * @param p the coordinates relative to the top left corner of the {@link #getContentComponent() content component}.
   */
  @NotNull VisualPosition xyToVisualPosition(@NotNull Point p);

  /** Same as {{@link #xyToVisualPosition(Point)}}, but allows specifying the point with higher precision. */
  @NotNull VisualPosition xyToVisualPosition(@NotNull Point2D p);

  default @NotNull Point offsetToXY(int offset) {
    return offsetToXY(offset, false, false);
  }

  /** @see #offsetToVisualPosition(int, boolean, boolean) */
  default @NotNull Point offsetToXY(int offset, boolean leanForward, boolean beforeSoftWrap) {
    VisualPosition visualPosition = offsetToVisualPosition(offset, leanForward, beforeSoftWrap);
    return visualPositionToXY(visualPosition);
  }

  default @NotNull Point2D offsetToPoint2D(int offset) {
    return offsetToPoint2D(offset, false, false);
  }

  /** @see #offsetToVisualPosition(int, boolean, boolean) */
  default @NotNull Point2D offsetToPoint2D(int offset, boolean leanForward, boolean beforeSoftWrap) {
    VisualPosition visualPosition = offsetToVisualPosition(offset, leanForward, beforeSoftWrap);
    return visualPositionToPoint2D(visualPosition);
  }

  default int visualLineToY(int visualLine) {
    return visualPositionToXY(new VisualPosition(visualLine, 0)).y;
  }

  default int yToVisualLine(int y) {
    return xyToVisualPosition(new Point(0, y)).line;
  }

  /**
   * Returns the range of Y coordinates corresponding to the given visual line (not including associated block inlays).
   *
   * @return array of length 2, containing boundaries of the target Y range
   */
  default int @NotNull [] visualLineToYRange(int visualLine) {
    int startY = visualLineToY(visualLine);
    int startOffset = visualPositionToOffset(new VisualPosition(visualLine, 0));
    FoldRegion foldRegion = getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    int endY = startY + (foldRegion instanceof CustomFoldRegion ? ((CustomFoldRegion)foldRegion).getHeightInPixels() : getLineHeight());
    return new int[]{startY, endY};
  }

  /**
   * Adds a listener for receiving notifications about mouse clicks in the editor and
   * the mouse entering/exiting the editor.
   */
  void addEditorMouseListener(@NotNull EditorMouseListener listener);

  /**
   * Adds a listener for receiving notifications about mouse clicks in the editor and
   * the mouse entering/exiting the editor.
   * The listener is removed when the given parent disposable is disposed.
   */
  default void addEditorMouseListener(@NotNull EditorMouseListener listener, @NotNull Disposable parentDisposable) {
    addEditorMouseListener(listener);
    Disposer.register(parentDisposable, () -> removeEditorMouseListener(listener));
  }

  /**
   * Removes a listener for receiving notifications about mouse clicks in the editor and
   * the mouse entering/exiting the editor.
   */
  void removeEditorMouseListener(@NotNull EditorMouseListener listener);

  /** Adds a listener for receiving notifications about mouse movement in the editor. */
  void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  /**
   * Adds a listener for receiving notifications about mouse movement in the editor.
   * The listener is removed when the given parent disposable is disposed.
   */
  default void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener, @NotNull Disposable parentDisposable) {
    addEditorMouseMotionListener(listener);
    Disposer.register(parentDisposable, () -> removeEditorMouseMotionListener(listener));
  }

  /** Removes a listener for receiving notifications about mouse movement in the editor. */
  void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  /** Returns whether this editor instance has been disposed. */
  boolean isDisposed();

  /** Returns the project to which the editor is related, if any. */
  @Nullable Project getProject();

  /**
   * <h3>Obsolescence notice</h3>
   * Use {@link #getDocument()}
   * and {@link com.intellij.openapi.fileEditor.FileDocumentManager#getFile(Document)} instead.
   * <pre>{@code
   * Document document = editor.getDocument();
   * VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
   * }</pre>
   * <p>
   * To get {@code PsiFile}, use {@link com.intellij.psi.PsiDocumentManager#getPsiFile(Document)}
   * <hr>
   * Returns the file being edited, if any. Often {@code null}.
   */
  @Nullable
  @Obsolete
  default VirtualFile getVirtualFile() {
    return null;
  }

  /** Returns whether the editor is in insert mode instead of overwrite mode. */
  boolean isInsertMode();

  /** Returns whether the editor is in block selection mode instead of offset-based selection. */
  boolean isColumnMode();

  /** Returns whether the editor is a one-line editor (used in a dialog control, for example). */
  boolean isOneLineMode();

  /** Returns the gutter instance for the editor, which can be used to draw custom text annotations in the gutter. */
  @NotNull EditorGutter getGutter();

  /**
   * Returns the editor area (text, gutter, folding outline and so on) in which the specified mouse event occurred.
   *
   * @return the editor area, or {@code null} if the event occurred over an unknown area.
   */
  @Nullable EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e);

  /**
   * Set up a header component for this text editor.
   * <p>
   * Please note that this is used for the textual find feature,
   * so your component will most probably be reset once the user presses Ctrl+F.
   *
   * @param header a component to set up as the header for this text editor, or {@code null} to remove existing one.
   */
  void setHeaderComponent(@Nullable JComponent header);

  /** Returns whether the editor has an active header component set up by {@link #setHeaderComponent(JComponent)}. */
  boolean hasHeaderComponent();

  /** @return the component set by {@link #setHeaderComponent(JComponent)}, or {@code null} if no header is currently installed. */
  @Nullable JComponent getHeaderComponent();

  @NotNull IndentsModel getIndentsModel();

  /** Returns the inlay model, which allows adding custom visual elements to the editor's representation. */
  @NotNull InlayModel getInlayModel();

  @NotNull EditorKind getEditorKind();

  default @NotNull EditorHighlighter getHighlighter() {
    return EditorCoreUtil.createEmptyHighlighter(getProject(), getDocument());
  }

  /**
   * The vertical distance, in pixels, between the top of the visual line
   * and the baseline of the text in that visual line.
   * <p>
   * To get the top of the visual line, see {@link #visualLineToY(int)}, {@link #visualPositionToXY(VisualPosition)}, etc.
   */
  default int getAscent() {
    // The actual implementation in EditorImpl is a bit more complex, but this gives an idea of how it's constructed.
    return EditorThreading.compute(
      () -> (getContentComponent().getFontMetrics(getColorsScheme().getFont(EditorFontType.PLAIN)).getAscent() *
                                     getColorsScheme().getLineSpacing())).intValue();
  }

  /**
   * Computes a range which is visible on screen, i.e., a pair of character offsets first visible/last visible onscreen.
   * When the entire editor window is visible, returns {@code (0-getDocument().getTextLength())} range.
   * By default, it retrieves the visible area from the scrolling pane attached to the editor component.
   * Can only be called from the EDT.
   */
  default @NotNull ProperTextRange calculateVisibleRange() {
    EditorThreading.assertInteractionAllowed();
    return EditorThreading.compute(() -> {
      Rectangle rect = getScrollingModel().getVisibleArea();
      LogicalPosition startPosition = xyToLogicalPosition(new Point(rect.x, rect.y));
      int visibleStart = logicalPositionToOffset(startPosition);
      LogicalPosition endPosition = xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));
      int visibleEnd = logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
      return new ProperTextRange(visibleStart, Math.max(visibleEnd, visibleStart));
    });
  }
}
