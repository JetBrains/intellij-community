// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * A custom visual element displayed in the editor.
 * <p>
 * Each inlay is associated with a certain offset in a document but is not represented in the document text in any way.
 * The inlay's offset in the document is updated on document changes just like for a zero-range {@link RangeMarker}.
 * <p>
 * There are several kinds of inlays:
 * <ul>
 * <li><b>Inline</b> inlays are inserted between characters in a line of text
 *     and move the remaining text of the line further to the end of the line.
 *     Examples of inline inlays are parameter hints or inferred types.
 * <li><b>After-line-end</b> inlays are appended to the end of a logical line.
 *     An example of after-line-end inlays are evaluated expressions during debugging.
 * <li><b>Block</b> inlays are inserted between lines of text
 *     and move the remaining lines of the file further down.
 *     An example of block inlays are author attributions.
 * </ul>
 * An inlay becomes invalid on explicit disposal, or when a document range that contains the inlay's offset is deleted.
 *
 * @see InlayModel
 */
public interface Inlay<T extends EditorCustomElementRenderer> extends Disposable, UserDataHolderEx {
  /** Returns the editor to which this custom visual element belongs. */
  @NotNull Editor getEditor();

  /** Defines the position of the inlay element relative to the containing text. */
  @NotNull Placement getPlacement();

  /**
   * Tells whether this element is valid.
   * <p>
   * An inlay becomes invalid on explicit disposal, or when a document range containing the inlay's offset is deleted.
   * It also becomes invalid on editor disposal.
   */
  boolean isValid();

  /**
   * Returns the inlay's position in the document.
   * This position is updated on document changes just like for a {@link RangeMarker}.
   */
  int getOffset();

  /** See {@link InlayProperties#relatesToPrecedingText(boolean)}. */
  boolean isRelatedToPrecedingText();

  /**
   * Returns the visual position of the inlay's left boundary.
   * For 'block' elements, this is just a visual position associated with the inlay's offset.
   */
  @NotNull VisualPosition getVisualPosition();

  /**
   * Returns the inlay element's bounds in the editor's screen coordinate system
   * if it's visible (not folded), otherwise {@code null}.
   */
  @Nullable Rectangle getBounds();

  /**
   * Returns the renderer, which defines the size and visual representation for this inlay.
   */
  @NotNull T getRenderer();

  /**
   * Returns the inlay's width.
   * The width is defined when the inlay is created, using information returned by the inlay's renderer.
   * To change the width, call {@link #update()}.
   */
  int getWidthInPixels();

  /**
   * Returns the inlay's height.
   * The height is defined when the inlay is created, using information returned by the inlay's renderer.
   * To change the height (supported for 'block' elements only), call {@link #update()}.
   */
  int getHeightInPixels();

  /**
   * Returns the {@link GutterIconRenderer} instance defining an icon displayed in gutter, and associated actions (supported for block inlays
   * at the moment). This provider is defined at inlay's creation using information returned by inlay's renderer. To change it,
   * {@link #update()} method should be called.
   *
   * @see EditorCustomElementRenderer#calcGutterIconRenderer(Inlay)
   */
  @Nullable GutterIconRenderer getGutterIconRenderer();

  /**
   * Updates inlay properties (width, height, gutter icon renderer) from inlay's renderer. Also, repaints the inlay.
   *
   * @see EditorCustomElementRenderer#calcWidthInPixels(Inlay)
   * @see EditorCustomElementRenderer#calcHeightInPixels(Inlay)
   * @see EditorCustomElementRenderer#calcGutterIconRenderer(Inlay)
   * @see #repaint()
   */
  void update();

  /**
   * Causes repaint of inlay in editor.
   */
  void repaint();

  /**
   * Returns properties specified at inlay creation.
   *
   * @see InlayModel#addInlineElement(int, InlayProperties, EditorCustomElementRenderer)
   * @see InlayModel#addBlockElement(int, InlayProperties, EditorCustomElementRenderer)
   * @see InlayModel#addAfterLineEndElement(int, InlayProperties, EditorCustomElementRenderer)
   */
  default @NotNull InlayProperties getProperties() {
    return new InlayProperties();
  }

  /**
   * @see #getPlacement()
   */
  enum Placement {INLINE, ABOVE_LINE, BELOW_LINE, AFTER_LINE_END}
}
