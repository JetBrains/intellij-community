// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Defines the size and representation of an {@link Inlay} in the editor.
 *
 * @see InlayModel#addInlineElement(int, boolean, EditorCustomElementRenderer)
 * @see InlayModel#addBlockElement(int, boolean, boolean, int, EditorCustomElementRenderer)
 * @see InlayModel#addAfterLineEndElement(int, boolean, EditorCustomElementRenderer)
 * @see Inlay#getRenderer()
 */
public interface EditorCustomElementRenderer {
  /**
   * Calculates the width of the inlay in pixels, during {@link Inlay#update()}.
   * <p>
   * The result is stored in the inlay, where it can be accessed via {@link Inlay#getWidthInPixels()}.
   * It is also passed on to {@link #paint(Inlay, Graphics, Rectangle, TextAttributes)}.
   * <p>
   * For inline and after-line-end inlays, it should always be a positive value.
   */
  int calcWidthInPixels(@NotNull Inlay inlay);

  /**
   * For block inlays, calculates the height of the inlay in pixels, during {@link Inlay#update()}.
   * The default implementation returns the line height of the editor.
   * <p>
   * The result is stored in the inlay, where it can be accessed via {@link Inlay#getHeightInPixels()}.
   * It is also passed on to {@link #paint(Inlay, Graphics, Rectangle, TextAttributes)}.
   */
  default int calcHeightInPixels(@NotNull Inlay inlay) {
    return inlay.getEditor().getLineHeight();
  }

  /**
   * Defines the appearance of an inlay.
   * <p>
   * For precise positioning on HiDPI screens,
   * override {@link #paint(Inlay, Graphics2D, Rectangle2D, TextAttributes)} instead.
   *
   * @param targetRegion   the region where painting should be performed.
   *                       The location of this rectangle is provided by the editor,
   *                       the size of the rectangle matches the inlay's width and height,
   *                       as provided by {@link #calcWidthInPixels(Inlay)} and {@link #calcHeightInPixels(Inlay)}.
   * @param textAttributes attributes of the surrounding text
   */
  default void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) { }

  /**
   * Defines the appearance of an inlay.
   * <p>
   * Either this method or {@link #paint(Inlay, Graphics, Rectangle, TextAttributes)} needs to be overridden only.
   *
   * @param targetRegion   the region where painting should be performed.
   *                       The location of this rectangle is provided by the editor,
   *                       the size of the rectangle approximately matches the inlay's width and height,
   *                       as provided by {@link #calcWidthInPixels(Inlay)} and {@link #calcHeightInPixels(Inlay)} &#x2014;
   *                       they can differ somewhat due to rounding to integer device pixels
   * @param textAttributes attributes of the surrounding text
   */
  default void paint(@NotNull Inlay inlay,
                     @NotNull Graphics2D g,
                     @NotNull Rectangle2D targetRegion,
                     @NotNull TextAttributes textAttributes) {
    Rectangle region =
      new Rectangle((int)targetRegion.getX(), (int)targetRegion.getY(), inlay.getWidthInPixels(), inlay.getHeightInPixels());
    paint(inlay, (Graphics)g, region, textAttributes);
  }

  /**
   * Returns the registered ID of the action group
   * that is used for building the inlay's context menu.
   * If {@code null} is returned (and {@link #getContextMenuGroup(Inlay)} also returns {@code null}),
   * the standard editor's context menu is used instead.
   */
  default @Nullable @NonNls String getContextMenuGroupId(@NotNull Inlay inlay) {
    return null;
  }

  /**
   * Returns the action group
   * that is used for building the inlay's context menu.
   * If {@code null} is returned (and {@link #getContextMenuGroupId(Inlay)} also returns {@code null}),
   * the standard editor's context menu is used instead.
   * <p>
   * This method takes preference over {@link #getContextMenuGroupId(Inlay)},
   * that is, if it returns a non-null value, the latter method won't be called.
   */
  default @Nullable ActionGroup getContextMenuGroup(@NotNull Inlay inlay) {
    return null;
  }


  /**
   * For block inlays, allows showing an icon for related actions in the gutter.
   * The icon is only rendered if its height is not larger than the inlay's height.
   * <p>
   * The returned renderer must implement {@code equals} based on its value, not its identity,
   * as {@link Inlay#update()} only updates the inlay's provider
   * if the returned instance is not equal to the previously defined one.
   */
  default @Nullable GutterIconRenderer calcGutterIconRenderer(@NotNull Inlay inlay) {
    return null;
  }
}
