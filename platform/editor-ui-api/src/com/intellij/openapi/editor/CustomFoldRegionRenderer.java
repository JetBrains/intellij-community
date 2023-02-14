// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * An interface, defining size and representation of custom fold regions in editor.
 *
 * @see FoldingModel#addCustomLinesFolding(int, int, CustomFoldRegionRenderer)
 */
@ApiStatus.Experimental
public interface CustomFoldRegionRenderer {
  /**
   * This defines horizontal size of custom fold region's placeholder. Returned value will define the result of
   * {@link CustomFoldRegion#getWidthInPixels()} and the width of {@code targetRegion} parameter passed to renderer's
   * {@link #paint(CustomFoldRegion, Graphics2D, Rectangle2D, TextAttributes)} method.
   */
  int calcWidthInPixels(@NotNull CustomFoldRegion region);

  /**
   * This defines vertical size of custom fold region's placeholder. Returned value will define the result of
   * {@link CustomFoldRegion#getWidthInPixels()} and the height of {@code targetRegion} parameter passed to renderer's
   * {@link #paint(CustomFoldRegion, Graphics2D, Rectangle2D, TextAttributes)} method. Minimum possible height currently is equal to
   * {@link Editor#getLineHeight()}, returned values, smaller than that, are automatically adjusted.
   */
  int calcHeightInPixels(@NotNull CustomFoldRegion region);

  /**
   * Defines the appearance of custom element.
   *
   * @param targetRegion region where painting should be performed, location of this rectangle is calculated by editor implementation,
   *                     dimensions of the rectangle approximately match element's width and height (provided by
   *                     {@link #calcWidthInPixels(CustomFoldRegion)} and {@link #calcHeightInPixels(CustomFoldRegion)}) -
   *                     they can differ somewhat due to rounding to whole number of device pixels.
   * @param textAttributes attributes of surrounding text
   */
  void paint(@NotNull CustomFoldRegion region,
             @NotNull Graphics2D g,
             @NotNull Rectangle2D targetRegion,
             @NotNull TextAttributes textAttributes);

  /**
   * Enables custom fold region to have a custom context menu in editor (displayed on mouse right click).
   */
  default @Nullable ActionGroup getContextMenuGroup(@NotNull CustomFoldRegion region) {
    return null;
  }

  /**
   * Enables custom fold region to have an icon in gutter and process mouse clicks over that icon.
   * <p>
   * Returned provider should have a meaningful implementation of {@code equals} method - {@link CustomFoldRegion#update()}
   * will correctly update the provider (only) if newly returned instance is not equal to the previously defined one.
   */
  default @Nullable GutterIconRenderer calcGutterIconRenderer(@NotNull CustomFoldRegion region) {
    return null;
  }
}
