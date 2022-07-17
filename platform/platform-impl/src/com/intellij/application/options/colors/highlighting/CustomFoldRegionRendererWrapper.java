// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.colors.highlighting;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.CustomFoldRegionRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static com.intellij.openapi.editor.colors.CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES;

public class CustomFoldRegionRendererWrapper implements CustomFoldRegionRenderer {
  private final CustomFoldRegionRenderer myDelegate;
  private final boolean myDrawBorder;

  public CustomFoldRegionRendererWrapper(CustomFoldRegionRenderer delegate, boolean drawBorder) {
    myDelegate = delegate;
    myDrawBorder = drawBorder;
  }

  @Override
  public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
    return myDelegate.calcWidthInPixels(region);
  }

  @Override
  public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
    return myDelegate.calcHeightInPixels(region);
  }

  @Override
  public void paint(@NotNull CustomFoldRegion region,
                    @NotNull Graphics2D g,
                    @NotNull Rectangle2D targetRegion,
                    @NotNull TextAttributes textAttributes) {
    myDelegate.paint(region, g, targetRegion, textAttributes);
    if (myDrawBorder) {
      TextAttributes attributes = region.getEditor().getColorsScheme().getAttributes(BLINKING_HIGHLIGHTS_ATTRIBUTES);
      if (attributes != null && attributes.getEffectColor() != null) {
        g.setColor(attributes.getEffectColor());
        Rectangle r = targetRegion.getBounds();
        g.drawRect(r.x, r.y, r.width, r.height);
      }
    }
  }
}
