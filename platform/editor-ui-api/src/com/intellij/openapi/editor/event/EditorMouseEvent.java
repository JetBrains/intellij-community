// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.EventObject;

/**
 * This class provides additional information about the mouse location, namely editor coordinates ({@link #getOffset()},
 * {@link #getLogicalPosition()}, {@link #getVisualPosition()}) and editor entities under mouse cursor ({@link #isOverText()},
 * {@link #getCollapsedFoldRegion()}, {@link #getInlay()}, {@link #getGutterIconRenderer()}). This information is calculated before event
 * dispatching, and is not guaranteed to be actual by the time some event listener receives the event (if previously called listener has
 * modified the editor state).
 * <p>
 * The additional information is not provided for {@link MouseEvent#MOUSE_ENTERED MOUSE_ENTERED} and
 * {@link MouseEvent#MOUSE_EXITED MOUSE_EXITED} events. Return values of corresponding event getters are unspecified for those events.
 */
public class EditorMouseEvent extends EventObject {
  private final @NotNull MouseEvent myMouseEvent;
  private final EditorMouseEventArea myEditorArea;
  private final int myOffset;
  private final LogicalPosition myLogicalPosition;
  private final VisualPosition myVisualPosition;
  private final boolean myIsOverText;
  private final FoldRegion myCollapsedFoldRegion;
  private final Inlay myInlay;
  private final GutterIconRenderer myGutterIconRenderer;

  public EditorMouseEvent(@NotNull Editor editor, @NotNull MouseEvent mouseEvent, EditorMouseEventArea area) {
    this(editor, mouseEvent, area, 0, new LogicalPosition(0, 0), new VisualPosition(0, 0), true, null , null, null);
  }

  public EditorMouseEvent(@NotNull Editor editor, @NotNull MouseEvent mouseEvent, EditorMouseEventArea area,
                          int offset, @NotNull LogicalPosition logicalPosition, @NotNull VisualPosition visualPosition,
                          boolean isOverText, FoldRegion collapsedFoldRegion, Inlay inlay, GutterIconRenderer gutterIconRenderer) {
    super(editor);

    myMouseEvent = mouseEvent;
    myEditorArea = area;
    myOffset = offset;
    myLogicalPosition = logicalPosition;
    myVisualPosition = visualPosition;
    myIsOverText = isOverText;
    myCollapsedFoldRegion = collapsedFoldRegion;
    myInlay = inlay;
    myGutterIconRenderer = gutterIconRenderer;
  }

  public @NotNull Editor getEditor() {
    return (Editor) getSource();
  }

  public @NotNull MouseEvent getMouseEvent() {
    return myMouseEvent;
  }

  public void consume() {
    myMouseEvent.consume();
  }

  public boolean isConsumed() {
    return myMouseEvent.isConsumed();
  }

  public EditorMouseEventArea getArea() {
    return myEditorArea;
  }

  public int getOffset() {
    return myOffset;
  }

  public @NotNull LogicalPosition getLogicalPosition() {
    return myLogicalPosition;
  }

  public @NotNull VisualPosition getVisualPosition() {
    return myVisualPosition;
  }

  /**
   * Returns {@code false} if mouse is below the last line of text, to the right of the last character on the line, or over an inlay.
   */
  public boolean isOverText() {
    return myIsOverText;
  }

  public @Nullable FoldRegion getCollapsedFoldRegion() {
    return myCollapsedFoldRegion == null || !myCollapsedFoldRegion.isValid() ? null : myCollapsedFoldRegion;
  }

  public @Nullable Inlay getInlay() {
    return myInlay == null || !myInlay.isValid() ? null : myInlay;
  }

  public @Nullable GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }
}
