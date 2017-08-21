/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * Represents an instance of the IDEA text editor.
 *
 * @see EditorFactory#createEditor(Document)
 * @see EditorFactory#createViewer(Document)
 */
public interface Editor extends UserDataHolder {
  Editor[] EMPTY_ARRAY = new Editor[0];

  /**
   * Returns the document edited or viewed in the editor.
   *
   * @return the document instance.
   */
  @NotNull
  Document getDocument();

  /**
   * Returns the value indicating whether the editor operates in viewer mode, with
   * all modification actions disabled.
   *
   * @return true if the editor works as a viewer, false otherwise
   */
  boolean isViewer();

  /**
   * Returns the component for the entire editor including the scrollbars, error stripe, gutter
   * and other decorations. The component can be used, for example, for converting logical to
   * screen coordinates.
   *
   * @return the component instance.
   */
  @NotNull
  JComponent getComponent();

  /**
   * Returns the component for the content area of the editor (the area displaying the document text).
   * The component can be used, for example, for converting logical to screen coordinates.
   * The instance is implementing {@link DataProvider}
   *
   * @return the component instance.
   */
  @NotNull
  JComponent getContentComponent();

  void setBorder(@Nullable Border border);

  Insets getInsets();

  /**
   * Returns the selection model for the editor, which can be used to select ranges of text in
   * the document and retrieve information about the selection.
   * <p>
   * To query or change selections for specific carets, {@link CaretModel} interface should be used.
   *
   * @see #getCaretModel()
   * 
   * @return the selection model instance.
   */
  @NotNull
  SelectionModel getSelectionModel();

  /**
   * Returns the markup model for the editor. This model contains editor-specific highlighters
   * (for example, highlighters added by "Highlight usages in file"), which are painted in addition
   * to the highlighters contained in the markup model for the document.
   * <p>
   * See also {@link com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(Document, Project, boolean)}
   *          {@link com.intellij.openapi.editor.ex.EditorEx#getFilteredDocumentMarkupModel()}.
   *
   * @return the markup model instance.
   */
  @NotNull
  MarkupModel getMarkupModel();

  /**
   * Returns the folding model for the document, which can be used to add, remove, expand
   * or collapse folded regions in the document.
   *
   * @return the folding model instance.
   */
  @NotNull
  FoldingModel getFoldingModel();

  /**
   * Returns the scrolling model for the document, which can be used to scroll the document
   * and retrieve information about the current position of the scrollbars.
   *
   * @return the scrolling model instance.
   */
  @NotNull
  ScrollingModel getScrollingModel();

  /**
   * Returns the caret model for the document, which can be used to add and remove carets to the editor, as well as to query and update 
   * carets' and corresponding selections' positions.
   *
   * @return the caret model instance.
   */
  @NotNull
  CaretModel getCaretModel();

  /**
   * Returns the soft wrap model for the document, which can be used to get information about soft wraps registered
   * for the editor document at the moment and provides basic management functions for them.
   *
   * @return the soft wrap model instance
   */
  @NotNull
  SoftWrapModel getSoftWrapModel();

  /**
   * Returns the editor settings for this editor instance. Changes to these settings affect
   * only the current editor instance.
   *
   * @return the settings instance.
   */
  @NotNull
  EditorSettings getSettings();

  /**
   * Returns the editor color scheme for this editor instance. Changes to the scheme affect
   * only the current editor instance.
   *
   * @return the color scheme instance.
   */
  @NotNull
  EditorColorsScheme getColorsScheme();

  /**
   * Returns the height of a single line of text in the current editor font.
   *
   * @return the line height in pixels.
   */
  int getLineHeight();

  /**
   * Maps a logical position in the editor to pixel coordinates.
   *
   * @param pos the logical position.
   * @return the coordinates relative to the top left corner of the {@link #getContentComponent() content component}.
   */
  @NotNull
  Point logicalPositionToXY(@NotNull LogicalPosition pos);

  /**
   * Maps a logical position in the editor to the offset in the document.
   *
   * @param pos the logical position.
   * @return the corresponding offset in the document.
   */
  int logicalPositionToOffset(@NotNull LogicalPosition pos);

  /**
   * Maps a logical position in the editor (the line and column ignoring folding) to
   * a visual position (with folded lines and columns not included in the line and column count).
   *
   * @param logicalPos the logical position.
   * @return the corresponding visual position.
   */
  @NotNull
  VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos);

  /**
   * Maps a visual position in the editor to pixel coordinates.
   *
   * @param visible the visual position.
   * @return the coordinates relative to the top left corner of the {@link #getContentComponent() content component}.
   */
  @NotNull
  Point visualPositionToXY(@NotNull VisualPosition visible);

  /**
   * Same as {@link #visualPositionToXY(VisualPosition)}, but returns potentially more precise result.
   */
  @NotNull
  Point2D visualPositionToPoint2D(@NotNull VisualPosition pos);

  /**
   * Maps a visual position in the editor (with folded lines and columns not included in the line and column count) to
   * a logical position (the line and column ignoring folding).
   *
   * @param visiblePos the visual position.
   * @return the corresponding logical position.
   */
  @NotNull
  LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos);

  /**
   * Maps an offset in the document to a logical position.
   * <p>
   * It's assumed that original position is associated with character immediately preceding given offset, so target logical position will 
   * have {@link LogicalPosition#leansForward leansForward} value set to {@code false}.
   *
   * @param offset the offset in the document.
   * @return the corresponding logical position.
   */
  @NotNull
  LogicalPosition offsetToLogicalPosition(int offset);

  /**
   * Maps an offset in the document to a visual position.
   * <p>
   * It's assumed that original position is associated with character immediately preceding given offset, 
   * {@link VisualPosition#leansRight leansRight} value for visual position will be determined correspondingly.
   * <p>
   * If there's a soft wrap at given offset, visual position on a line following the wrap will be returned.
   *
   * @param offset the offset in the document.
   * @return the corresponding visual position.
   */
  @NotNull
  VisualPosition offsetToVisualPosition(int offset);

  /**
   * Maps an offset in the document to a visual position.
   *
   * @param offset the offset in the document.
   * @param leanForward if {@code true}, original position is associated with character after given offset, if {@code false} -
   *                    with character before given offset. This can make a difference in bidirectional text (see {@link LogicalPosition},
   *                    {@link VisualPosition})
   * @param beforeSoftWrap if {@code true}, visual position at line preceeding the wrap will be returned, otherwise - visual position
   *                       at line following the wrap.
   * @return the corresponding visual position.
   */
  @NotNull
  VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap);

  /**
   * Maps the pixel coordinates in the editor to a logical position.
   *
   * @param p the coordinates relative to the top left corner of the {@link #getContentComponent() content component}.
   * @return the corresponding logical position.
   */
  @NotNull
  LogicalPosition xyToLogicalPosition(@NotNull Point p);

  /**
   * Maps the pixel coordinates in the editor to a visual position.
   *
   * @param p the coordinates relative to the top left corner of the {@link #getContentComponent() content component}.
   * @return the corresponding visual position.
   */
  @NotNull
  VisualPosition xyToVisualPosition(@NotNull Point p);

  /**
   * Same as {{@link #xyToVisualPosition(Point)}}, but allows to specify target point with higher precision.
   */
  @NotNull
  VisualPosition xyToVisualPosition(@NotNull Point2D p);

  /**
   * @since 2017.2
   */
  @NotNull
  default Point offsetToXY(int offset) {
    return offsetToXY(offset, false, false);
  }

  /**
   * @see #offsetToVisualPosition(int, boolean, boolean)
   * @since 2017.2
   */
  @NotNull
  default Point offsetToXY(int offset, boolean leanForward, boolean beforeSoftWrap) {
    VisualPosition visualPosition = offsetToVisualPosition(offset, leanForward, beforeSoftWrap);
    return visualPositionToXY(visualPosition);
  }

  /**
   * @since 2017.2
   */
  @NotNull
  default Point2D offsetToPoint2D(int offset) {
    return offsetToPoint2D(offset, false, false);
  }

  /**
   * @see #offsetToVisualPosition(int, boolean, boolean)
   * @since 2017.2
   */
  @NotNull
  default Point2D offsetToPoint2D(int offset, boolean leanForward, boolean beforeSoftWrap) {
    VisualPosition visualPosition = offsetToVisualPosition(offset, leanForward, beforeSoftWrap);
    return visualPositionToPoint2D(visualPosition);
  }

  /**
   * Adds a listener for receiving notifications about mouse clicks in the editor and
   * the mouse entering/exiting the editor.
   *
   * @param listener the listener instance.
   */
  void addEditorMouseListener(@NotNull EditorMouseListener listener);

  /**
   * Removes a listener for receiving notifications about mouse clicks in the editor and
   * the mouse entering/exiting the editor.
   *
   * @param listener the listener instance.
   */
  void removeEditorMouseListener(@NotNull EditorMouseListener listener);

  /**
   * Adds a listener for receiving notifications about mouse movement in the editor.
   *
   * @param listener the listener instance.
   */
  void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  /**
   * Removes a listener for receiving notifications about mouse movement in the editor.
   *
   * @param listener the listener instance.
   */
  void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  /**
   * Checks if this editor instance has been disposed.
   *
   * @return true if the editor has been disposed, false otherwise.
   */
  boolean isDisposed();

  /**
   * Returns the project to which the editor is related.
   *
   * @return the project instance, or null if the editor is not related to any project.
   */
  @Nullable
  Project getProject();

  /**
   * Returns the insert/overwrite mode for the editor.
   *
   * @return true if the editor is in insert mode, false otherwise.
   */
  boolean isInsertMode();

  /**
   * Returns the block selection mode for the editor.
   *
   * @return true if the editor uses column selection, false if it uses regular selection.
   */
  boolean isColumnMode();

  /**
   * Checks if the current editor instance is a one-line editor (used in a dialog control, for example).
   *
   * @return true if the editor is one-line, false otherwise.
   */
  boolean isOneLineMode();

  /**
   * Returns the gutter instance for the editor, which can be used to draw custom text annotations
   * in the gutter.
   *
   * @return the gutter instance.
   */
  @NotNull
  EditorGutter getGutter();

  /**
   * Returns the editor area (text, gutter, folding outline and so on) in which the specified
   * mouse event occurred.
   *
   * @param e the mouse event for which the area is requested.
   * @return the editor area, or null if the event occurred over an unknown area.
   */
  @Nullable
  EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e);

  /**
   * Set up a header component for this text editor. Please note this is used for textual find feature so your component will most
   * probably will be reset once the user presses Ctrl+F.
   *
   * @param header a component to setup as header for this text editor or {@code null} to remove one.
   */
  void setHeaderComponent(@Nullable JComponent header);

  /**
   * @return {@code true} if this editor has active header component set up by {@link #setHeaderComponent(JComponent)}
   */
  boolean hasHeaderComponent();

  /**
   * @return a component set by {@link #setHeaderComponent(JComponent)} or {@code null} if no header currently installed.
   */
  @Nullable
  JComponent getHeaderComponent();

  @NotNull
  IndentsModel getIndentsModel();

  @NotNull
  InlayModel getInlayModel();

  @NotNull
  EditorKind getEditorKind();
}
