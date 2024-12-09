// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * {@link FoldRegion} with a custom (size and rendered content) placeholder representation. Such a region can only spans whole-lines
 * document range, and cannot be expanded.
 *
 * @see FoldingModel#addCustomLinesFolding(int, int, CustomFoldRegionRenderer)
 */
@ApiStatus.Experimental
public interface CustomFoldRegion extends FoldRegion {
  /**
   * Marker key for the folding regions, that are created to for a special, hidden or unmodifiable for user, regions,
   * for example - for notebook-files cell separators. Marked regions will be properly synchronized in Gateway.
   * Example of setting the key:
   * <br/>
   * {@code foldingRegion.putUserData(FrontendEditorFoldingModelAdapter.FRONTEND_FOLD_REGION, true)}
   */
  @ApiStatus.Internal
  Key<Boolean> IMMUTABLE_FOLD_REGION = Key.create("IMMUTABLE_FOLD_REGION");

  /**
   * Renderer for this fold region (specified at creation time).
   */
  @NotNull CustomFoldRegionRenderer getRenderer();

  /**
   * Current width of fold region placeholder in editor. Width is defined at fold region's creation using information provided by region's
   * renderer, and is re-evaluated on calling {@link #update()}.
   *
   * @see CustomFoldRegionRenderer#calcWidthInPixels(CustomFoldRegion)
   */
  int getWidthInPixels();

  /**
   * Current height of fold region placeholder in editor. Height is defined at fold region's creation using information provided by region's
   * renderer, and is re-evaluated on calling {@link #update()}.
   *
   * @see CustomFoldRegionRenderer#calcHeightInPixels(CustomFoldRegion)
   */
  int getHeightInPixels();

  /**
   * Returns {@link GutterIconRenderer} instance defining an icon displayed in gutter, and associated actions, for the fold region.
   * This provider is defined at region's creation using information returned by region's renderer, and is re-evaluated on calling
   * {@link #update()}.
   *
   * @see CustomFoldRegionRenderer#calcGutterIconRenderer(CustomFoldRegion)
   */
  @Nullable GutterIconRenderer getGutterIconRenderer();

  /**
   * Updates fold region's properties (width, height, gutter icon renderer) from its renderer. Also, repaints the region's placeholder.
   *
   * @see CustomFoldRegionRenderer#calcWidthInPixels(CustomFoldRegion)
   * @see CustomFoldRegionRenderer#calcHeightInPixels(CustomFoldRegion)
   * @see CustomFoldRegionRenderer#calcGutterIconRenderer(CustomFoldRegion)
   * @see #repaint()
   */
  void update();

  /**
   * Causes repaint of fold region's placeholder in editor.
   */
  void repaint();

  /**
   * Returns the location of fold region's placeholder in editor coordinate system, or {@code null} if the placeholder isn't visible
   * currently (due to being 'inside' another collapsed fold region).
   */
  @Nullable Point getLocation();
}
