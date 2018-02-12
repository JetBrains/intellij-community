// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A custom visual element displayed in editor. It is associated with a certain position in a document, but is not
 * represented in document text in any way. Inlay's document position (offset) is updated on document changes just like
 * for a {@link RangeMarker}. Inlay becomes invalid on explicit disposal, or when a document range fully containing inlay's offset,
 * is deleted.
 * <p>
 * WARNING! This is an experimental API, it can change at any time.
 */
@ApiStatus.Experimental
public interface Inlay extends Disposable, UserDataHolderEx {
  /**
   * Tells whether this element is valid. Inlay becomes invalid on explicit disposal,
   * or when a document range fully containing inlay's offset, is deleted.
   */
  boolean isValid();

  /**
   * Returns current inlay's position in the document. This position is updated on document changes just like for a {@link RangeMarker}.
   */
  int getOffset();

  /**
   * Tells whether this element is associated with preceding or following text. This relation defines certain aspects of inlay's behaviour
   * with respect to changes in editor, e.g. when text is inserted at inlay's position, inlay will end up before the inserted text if the
   * returned value is {@code false} and after the text, if the returned value is {@code true}.
   * Also, when {@link Caret#moveToOffset(int)} or similar offset-based method is invoked, and an inlay exists at the given offset,
   * caret will be positioned to the left of inlay if returned value is {@code true}, and vice versa.
   * <p>
   * The value is determined at element's
   * creation (see {@link InlayModel#addInlineElement(int, boolean, EditorCustomElementRenderer)}.
   */
  boolean isRelatedToPrecedingText();
  
  /**
   * Returns current visual position of the inlay's left boundary.
   */
  @NotNull
  VisualPosition getVisualPosition();

  /**
   * Returns renderer, which defines size and representation for this inlay.
   */
  @NotNull
  EditorCustomElementRenderer getRenderer();

  /**
   * Returns current inlay's width. Width is defined at inlay's creation using information returned by inlay's renderer.
   * To change width, {@link #updateSize()} method should be called.
   */
  int getWidthInPixels();

  /**
   * Updates inlay's size by querying information from inlay's renderer.
   *
   * @see EditorCustomElementRenderer#calcWidthInPixels(Editor)
   */
  void updateSize();

  /**
   * Causes repaint of inlay in editor.
   */
  void repaint();
}
